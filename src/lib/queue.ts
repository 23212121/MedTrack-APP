import { NotificationChannel, VisitStatus } from "@prisma/client";
import { prisma } from "./prisma";
import { queueVisitNotification } from "./notifications";

const QUEUE_STATUSES: VisitStatus[] = ["CHECKED_IN", "IN_CONSULT"];

export async function getDoctorQueue(doctorId: string) {
  const dayStart = new Date();
  dayStart.setHours(0, 0, 0, 0);

  return prisma.visit.findMany({
    where: {
      doctorId,
      status: { in: QUEUE_STATUSES },
      OR: [
        { checkedInAt: { gte: dayStart } },
        { scheduledStart: { gte: dayStart } },
      ],
    },
    orderBy: [
      { tokenNumber: "asc" },
      { checkedInAt: "asc" },
      { scheduledStart: "asc" },
    ],
    include: {
      patient: true,
      doctor: { include: { user: true } },
      clinic: true,
    },
  });
}

export type PatientQueueView = {
  queuePosition: number | null;
  patientsAhead: number;
  fixedConsultMinutes: number;
  estimatedConsultAt: Date | null;
  estimatedWaitMinutes: number | null;
  nowServingToken: number | null;
  doctorName: string | null;
  isDoctorLate: boolean;
};

export async function buildPatientQueueView(
  visitId: string
): Promise<PatientQueueView | null> {
  const visit = await prisma.visit.findUnique({
    where: { id: visitId },
    include: {
      doctor: { include: { user: true } },
      clinic: true,
    },
  });
  if (!visit?.doctorId) {
    return {
      queuePosition: null,
      patientsAhead: 0,
      fixedConsultMinutes: 15,
      estimatedConsultAt: null,
      estimatedWaitMinutes: null,
      nowServingToken: null,
      doctorName: null,
      isDoctorLate: false,
    };
  }

  const fixed =
    visit.doctor?.fixedConsultMinutes ??
    visit.clinic.fixedConsultMinutes ??
    visit.doctor?.avgConsultMinutes ??
    15;

  const queue = await getDoctorQueue(visit.doctorId);
  const index = queue.findIndex((v) => v.id === visit.id);
  const inConsult = queue.find((v) => v.status === "IN_CONSULT");
  const patientsAhead =
    index >= 0
      ? index
      : queue.filter(
          (v) =>
            visit.tokenNumber != null &&
            v.tokenNumber != null &&
            v.tokenNumber < visit.tokenNumber
        ).length;

  const queuePosition = index >= 0 ? index + 1 : null;

  let estimatedWaitMinutes: number | null = null;
  if (visit.status === "IN_CONSULT") {
    estimatedWaitMinutes = 0;
  } else if (visit.status === "CHECKED_IN" || visit.status === "BOOKED") {
    let wait = visit.delayMinutes || 0;
    const aheadSlice = index >= 0 ? queue.slice(0, index) : [];
    for (const ahead of aheadSlice) {
      if (ahead.status === "IN_CONSULT" && ahead.actualStart) {
        const elapsed = Math.round(
          (Date.now() - ahead.actualStart.getTime()) / 60_000
        );
        wait += Math.max(0, fixed - elapsed);
      } else {
        wait += fixed;
      }
    }
    estimatedWaitMinutes = wait;
  }

  const estimatedConsultAt =
    estimatedWaitMinutes != null
      ? new Date(Date.now() + estimatedWaitMinutes * 60_000)
      : visit.scheduledStart;

  const isDoctorLate =
    visit.delayMinutes > 0 ||
    (!!visit.scheduledStart &&
      !visit.actualStart &&
      Date.now() >
        visit.scheduledStart.getTime() +
          visit.clinic.lateGraceMinutes * 60_000);

  return {
    queuePosition,
    patientsAhead: Math.max(0, patientsAhead),
    fixedConsultMinutes: fixed,
    estimatedConsultAt,
    estimatedWaitMinutes,
    nowServingToken: inConsult?.tokenNumber ?? null,
    doctorName: visit.doctor?.user.fullName ?? null,
    isDoctorLate,
  };
}

const ALL_CHANNELS: NotificationChannel[] = [
  NotificationChannel.SMS,
  NotificationChannel.EMAIL,
  NotificationChannel.PUSH,
];

/** After one visit completes: promote next token and notify waiting patients. */
export async function advanceQueueAfterComplete(
  completedVisitId: string,
  actorUserId: string
) {
  const completed = await prisma.visit.findUnique({
    where: { id: completedVisitId },
    include: { clinic: true, doctor: true },
  });
  if (!completed?.doctorId) return;

  const queue = await getDoctorQueue(completed.doctorId);
  const waiting = queue.filter((v) => v.status === "CHECKED_IN");
  const next = waiting[0];
  const threshold = completed.clinic.youAreNextThreshold;

  if (next) {
    await prisma.visitEvent.create({
      data: {
        visitId: next.id,
        actorUserId,
        eventType: "QUEUE",
        message: `You are next after token ${completed.tokenNumber ?? "—"} completed`,
        metadataJson: JSON.stringify({
          previousVisitId: completedVisitId,
          token: next.tokenNumber,
        }),
      },
    });

    await queueVisitNotification(next.id, "YOU_ARE_NEXT", ALL_CHANNELS, {
      allowRepeat: false,
      extraVars: {
        previous_token: completed.tokenNumber ?? "—",
        queue_position: 1,
      },
    });
  }

  // Remaining waiting patients get timeline update with revised ETA
  for (let i = 0; i < waiting.length; i++) {
    const v = waiting[i];
    if (next && v.id === next.id) continue;
    if (i + 1 > threshold + 3) break;

    const view = await buildPatientQueueView(v.id);
    await queueVisitNotification(v.id, "STATUS_UPDATE", ALL_CHANNELS, {
      allowRepeat: true,
      extraVars: {
        queue_position: view?.queuePosition ?? i + 1,
        estimated_time: view?.estimatedConsultAt
          ? view.estimatedConsultAt.toLocaleTimeString()
          : "soon",
        patients_ahead: view?.patientsAhead ?? i,
        now_serving: view?.nowServingToken ?? "—",
      },
    });
  }
}

/** Doctor running late / consult overrunning — alert next waiting patients. */
export async function notifyQueueDelay(
  doctorId: string,
  delayMinutes: number,
  actorUserId: string,
  sourceVisitId?: string
) {
  const queue = await getDoctorQueue(doctorId);
  const waiting = queue.filter(
    (v) => v.status === "CHECKED_IN" && v.id !== sourceVisitId
  );

  for (const v of waiting) {
    const clinic = v.clinic;
    if (v.delayAlertsSent >= clinic.maxDelayAlertsPerVisit) continue;

    const updated = await prisma.visit.update({
      where: { id: v.id },
      data: {
        delayMinutes: (v.delayMinutes || 0) + delayMinutes,
        delayAlertsSent: { increment: 1 },
        events: {
          create: {
            actorUserId,
            eventType: "DELAY",
            message: `Queue delayed +${delayMinutes} min (doctor running long)`,
          },
        },
      },
    });

    await queueVisitNotification(updated.id, "DOCTOR_DELAYED", ALL_CHANNELS, {
      allowRepeat: true,
      extraVars: {
        delay_minutes: updated.delayMinutes,
        estimated_time: new Date(
          Date.now() + updated.delayMinutes * 60_000
        ).toLocaleTimeString(),
      },
    });
  }
}
