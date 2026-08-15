import {
  NotificationChannel,
  NotificationStatus,
  Prisma,
} from "@prisma/client";
import { prisma } from "./prisma";

type TemplateVars = Record<string, string | number | null | undefined>;

const DEFAULT_BODIES: Record<string, string> = {
  BOOKING_CONFIRMED:
    "{{clinic_name}}: Hi {{patient_name}}, your visit with {{doctor_name}} is booked for {{scheduled_time}}. Token will be issued at check-in.",
  CHECKED_IN:
    "{{clinic_name}}: Checked in. Token {{token}} for {{doctor_name}}. Fixed consult time {{fixed_minutes}} min. Approx consult around {{estimated_time}}.",
  CHECKUP_STARTED:
    "{{clinic_name}}: Your checkup with {{doctor_name}} has started. Token {{token}}.",
  DOCTOR_DELAYED:
    "{{clinic_name}}: {{doctor_name}} is running about {{delay_minutes}} minutes late. Your updated consult time is around {{estimated_time}}. Token {{token}}.",
  YOU_ARE_NEXT:
    "{{clinic_name}}: You're next (token {{token}}) for {{doctor_name}}. Please be ready near the clinic.",
  STATUS_UPDATE:
    "{{clinic_name}}: Timeline update — token {{token}}, position {{queue_position}}, about {{patients_ahead}} ahead. Estimated consult {{estimated_time}} with {{doctor_name}}.",
  VISIT_COMPLETED:
    "{{clinic_name}}: Visit with {{doctor_name}} is complete. Total fee: {{fee_currency}} {{total_fee}}.",
  OVERTIME_FEE:
    "{{clinic_name}}: Consult exceeded fixed time. Extra charge {{overtime_fee}} applied. Total {{fee_currency}} {{total_fee}}.",
};

function renderTemplate(template: string, vars: TemplateVars) {
  return template.replace(/\{\{(\w+)\}\}/g, (_, key: string) => {
    const value = vars[key];
    return value === null || value === undefined ? "" : String(value);
  });
}

export type NotifyOptions = {
  allowRepeat?: boolean;
  extraVars?: TemplateVars;
};

/** Persist + send SMS / email / in-app (PUSH) for visit lifecycle. */
export async function queueVisitNotification(
  visitId: string,
  eventCode: string,
  channels: NotificationChannel[] = [
    NotificationChannel.SMS,
    NotificationChannel.EMAIL,
    NotificationChannel.PUSH,
  ],
  options: NotifyOptions = {}
) {
  const visit = await prisma.visit.findUnique({
    where: { id: visitId },
    include: {
      patient: { include: { user: true } },
      doctor: { include: { user: true } },
      clinic: true,
    },
  });
  if (!visit) return;

  const fixed =
    visit.doctor?.fixedConsultMinutes ??
    visit.clinic.fixedConsultMinutes ??
    15;

  const estimated =
    visit.scheduledStart && visit.delayMinutes
      ? new Date(visit.scheduledStart.getTime() + visit.delayMinutes * 60_000)
      : visit.scheduledStart;

  const vars: TemplateVars = {
    patient_name: visit.patient.fullName,
    doctor_name: visit.doctor?.user.fullName ?? "your doctor",
    clinic_name: visit.clinic.name,
    scheduled_time: visit.scheduledStart
      ? visit.scheduledStart.toLocaleString()
      : "TBD",
    token: visit.tokenNumber ?? "—",
    delay_minutes: visit.delayMinutes,
    overtime_fee: visit.overtimeFee,
    total_fee: visit.totalFee ?? visit.baseFee ?? "",
    fee_currency: visit.feeCurrency ?? "INR",
    status: visit.status,
    fixed_minutes: fixed,
    estimated_time: estimated
      ? estimated.toLocaleTimeString()
      : "to be announced",
    queue_position: "—",
    patients_ahead: "—",
    now_serving: "—",
    previous_token: "—",
    ...options.extraVars,
  };

  const targets: {
    channel: NotificationChannel;
    recipient: string | null | undefined;
    consent: boolean;
  }[] = [
    {
      channel: NotificationChannel.SMS,
      recipient: visit.patient.phone,
      consent: visit.patient.smsConsent,
    },
    {
      channel: NotificationChannel.EMAIL,
      recipient: visit.patient.email,
      consent: visit.patient.emailConsent && !!visit.patient.email,
    },
    {
      channel: NotificationChannel.PUSH,
      recipient: visit.patient.userId
        ? `app:${visit.patient.userId}`
        : `app-patient:${visit.patientId}`,
      consent: true,
    },
  ].filter((t) => channels.includes(t.channel));

  for (const item of targets) {
    if (!item.recipient) continue;

    if (!options.allowRepeat) {
      const existing = await prisma.notification.findFirst({
        where: {
          visitId: visit.id,
          eventCode,
          channel: item.channel,
          status: { in: [NotificationStatus.SENT, NotificationStatus.PENDING] },
        },
      });
      if (existing) continue;
    }

    const template = await prisma.notificationTemplate.findUnique({
      where: {
        clinicId_eventCode_channel: {
          clinicId: visit.clinicId,
          eventCode,
          channel: item.channel,
        },
      },
    });

    if (template && !template.isEnabled) continue;

    const body = renderTemplate(
      template?.bodyTemplate ??
        DEFAULT_BODIES[eventCode] ??
        "{{clinic_name}}: Update {{status}} for {{patient_name}}.",
      vars
    );
    const subject = template?.subject
      ? renderTemplate(template.subject, vars)
      : `${eventCode.replaceAll("_", " ")} — ${visit.clinic.name}`;

    const status = item.consent
      ? NotificationStatus.SENT
      : NotificationStatus.SKIPPED;

    const notification = await prisma.notification.create({
      data: {
        clinicId: visit.clinicId,
        visitId: visit.id,
        patientId: visit.patientId,
        eventCode,
        channel: item.channel,
        recipient: item.recipient,
        subject,
        body,
        status,
        sentAt: status === NotificationStatus.SENT ? new Date() : null,
        providerRef:
          status === NotificationStatus.SENT
            ? item.channel === NotificationChannel.PUSH
              ? "in-app"
              : "console-provider"
            : null,
      },
    });

    if (status === NotificationStatus.SENT) {
      console.log(`[notify:${item.channel}] to=${item.recipient} :: ${body}`);
    }

    await prisma.visitEvent.create({
      data: {
        visitId: visit.id,
        eventType: "NOTIFY",
        message: `${eventCode} ${item.channel} ${status}`,
        metadataJson: JSON.stringify({ notificationId: notification.id }),
      },
    });
  }
}

export async function queueBookingConfirmed(visitId: string) {
  return queueVisitNotification(visitId, "BOOKING_CONFIRMED");
}

export async function createVisitWithTimeline(
  data: Prisma.VisitCreateInput,
  actorUserId: string,
  staffNote?: string
) {
  const visit = await prisma.visit.create({
    data: {
      ...data,
      events: {
        create: {
          actorUserId,
          eventType: "STATUS_CHANGE",
          toStatus: data.status ?? "CALLED",
          message: "Visit created",
        },
      },
      ...(staffNote
        ? {
            notes: {
              create: {
                authorUserId: actorUserId,
                noteType: "STAFF" as const,
                body: staffNote,
              },
            },
          }
        : {}),
    },
    include: {
      patient: true,
      doctor: { include: { user: true } },
      events: true,
      notes: true,
    },
  });

  if (visit.status === "BOOKED") {
    await queueBookingConfirmed(visit.id);
  }

  return prisma.visit.findUniqueOrThrow({
    where: { id: visit.id },
    include: {
      patient: true,
      doctor: { include: { user: true } },
      department: true,
      events: { orderBy: { createdAt: "asc" } },
      notes: true,
      notifications: true,
    },
  });
}
