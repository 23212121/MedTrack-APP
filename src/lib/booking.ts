import { VisitStatus } from "@prisma/client";
import { prisma } from "./prisma";
import { queueBookingConfirmed } from "./notifications";

const ACTIVE_STATUSES: VisitStatus[] = [
  VisitStatus.CALLED,
  VisitStatus.BOOKED,
  VisitStatus.CHECKED_IN,
  VisitStatus.IN_CONSULT,
];

export type PublicSlot = {
  startsAt: string;
  endsAt: string;
  tokenNumber: number;
  label: string;
};

function parseTimeOnDate(dateYmd: string, hhmm: string) {
  const [h, m] = hhmm.split(":").map(Number);
  const d = new Date(`${dateYmd}T00:00:00`);
  d.setHours(h, m, 0, 0);
  return d;
}

function dayBounds(dateYmd: string) {
  const start = new Date(`${dateYmd}T00:00:00`);
  const end = new Date(`${dateYmd}T23:59:59.999`);
  return { start, end };
}

export async function getAvailableSlots(
  doctorId: string,
  dateYmd: string
): Promise<{
  slots: PublicSlot[];
  schedules: { startTime: string; endTime: string; slotMinutes: number }[];
  workingDay: boolean;
}> {
  const dayOfWeek = new Date(`${dateYmd}T12:00:00`).getDay();
  const schedules = await prisma.doctorSchedule.findMany({
    where: { doctorId, dayOfWeek },
    orderBy: { startTime: "asc" },
  });

  if (!schedules.length) {
    return { slots: [], schedules: [], workingDay: false };
  }

  const { start: dayStart, end: dayEnd } = dayBounds(dateYmd);
  const existing = await prisma.visit.findMany({
    where: {
      doctorId,
      status: { in: ACTIVE_STATUSES },
      scheduledStart: { gte: dayStart, lte: dayEnd },
    },
    select: { scheduledStart: true, tokenNumber: true },
    orderBy: { scheduledStart: "asc" },
  });

  const taken = new Set(
    existing
      .filter((v) => v.scheduledStart)
      .map((v) => v.scheduledStart!.getTime())
  );

  const leaveBlocks = await prisma.doctorAvailability.findMany({
    where: {
      doctorId,
      availabilityType: { in: ["LEAVE", "BUSY", "BLOCKED"] },
      startsAt: { lte: dayEnd },
      endsAt: { gte: dayStart },
    },
  });

  const now = Date.now();
  const openSlots: Omit<PublicSlot, "tokenNumber">[] = [];

  for (const schedule of schedules) {
    const slotMinutes = schedule.slotMinutes || 15;
    let cursor = parseTimeOnDate(dateYmd, schedule.startTime);
    const end = parseTimeOnDate(dateYmd, schedule.endTime);

    while (cursor.getTime() + slotMinutes * 60_000 <= end.getTime()) {
      const startsAt = new Date(cursor);
      const endsAt = new Date(cursor.getTime() + slotMinutes * 60_000);
      const t = startsAt.getTime();

      const onLeave = leaveBlocks.some(
        (b) => t < b.endsAt.getTime() && endsAt.getTime() > b.startsAt.getTime()
      );

      if (!onLeave && !taken.has(t) && t > now) {
        openSlots.push({
          startsAt: startsAt.toISOString(),
          endsAt: endsAt.toISOString(),
          label: startsAt.toLocaleTimeString([], {
            hour: "numeric",
            minute: "2-digit",
          }),
        });
      }

      cursor = endsAt;
    }
  }

  const bookedCount = existing.length;
  const slots: PublicSlot[] = openSlots.map((s, i) => ({
    ...s,
    tokenNumber: bookedCount + i + 1,
  }));

  return {
    slots,
    schedules: schedules.map((s) => ({
      startTime: s.startTime,
      endTime: s.endTime,
      slotMinutes: s.slotMinutes,
    })),
    workingDay: true,
  };
}

export async function createPublicBooking(input: {
  clinicId: string;
  doctorId: string;
  patientName: string;
  phone: string;
  age?: number;
  gender?: "MALE" | "FEMALE" | "OTHER" | "UNKNOWN";
  address?: string;
  reason?: string;
  appointmentDate: string;
  scheduledStart: string;
}) {
  const doctor = await prisma.doctor.findFirst({
    where: {
      id: input.doctorId,
      clinicId: input.clinicId,
      isActive: true,
    },
    include: {
      user: { select: { fullName: true, email: true } },
      department: true,
      clinic: true,
    },
  });
  if (!doctor) {
    throw new BookingError("Doctor not found for this hospital", 404);
  }

  const startsAt = new Date(input.scheduledStart);
  if (Number.isNaN(startsAt.getTime()) || startsAt.getTime() <= Date.now()) {
    throw new BookingError("Please choose a future appointment time", 400);
  }

  const { slots } = await getAvailableSlots(
    input.doctorId,
    input.appointmentDate
  );
  const chosen = slots.find(
    (s) => new Date(s.startsAt).getTime() === startsAt.getTime()
  );
  if (!chosen) {
    throw new BookingError(
      "That time slot is no longer available. Please pick another.",
      409
    );
  }

  const phone = input.phone.replace(/\D/g, "");
  if (phone.length < 8) {
    throw new BookingError("Enter a valid phone number", 400);
  }

  let dateOfBirth: Date | undefined;
  if (input.age != null && input.age > 0 && input.age < 130) {
    const y = new Date().getFullYear() - input.age;
    dateOfBirth = new Date(`${y}-01-01T00:00:00`);
  }

  const reasonParts = [
    input.reason?.trim(),
    input.address?.trim() ? `Area: ${input.address.trim()}` : null,
  ].filter(Boolean);

  let patient = await prisma.patient.findFirst({
    where: {
      clinicId: input.clinicId,
      phone,
      fullName: { equals: input.patientName.trim() },
    },
  });

  if (!patient) {
    patient = await prisma.patient.create({
      data: {
        clinicId: input.clinicId,
        phone,
        fullName: input.patientName.trim(),
        gender: input.gender ?? "UNKNOWN",
        dateOfBirth: dateOfBirth ?? null,
        smsConsent: true,
        emailConsent: false,
      },
    });
  }

  const duplicate = await prisma.visit.findFirst({
    where: {
      patientId: patient.id,
      doctorId: doctor.id,
      status: { in: ACTIVE_STATUSES },
      scheduledStart: {
        gte: dayBounds(input.appointmentDate).start,
        lte: dayBounds(input.appointmentDate).end,
      },
    },
  });
  if (duplicate) {
    throw new BookingError(
      "You already have an appointment with this doctor on that date",
      409
    );
  }

  const endsAt = new Date(chosen.endsAt);
  const visit = await prisma.visit.create({
    data: {
      clinic: { connect: { id: input.clinicId } },
      patient: { connect: { id: patient.id } },
      doctor: { connect: { id: doctor.id } },
      ...(doctor.departmentId
        ? { department: { connect: { id: doctor.departmentId } } }
        : {}),
      status: VisitStatus.BOOKED,
      tokenNumber: chosen.tokenNumber,
      reason: reasonParts.join(" · ") || null,
      scheduledStart: startsAt,
      scheduledEnd: endsAt,
      baseFee: doctor.baseConsultFee,
      feeCurrency: "INR",
      events: {
        create: {
          eventType: "STATUS_CHANGE",
          toStatus: VisitStatus.BOOKED,
          message: "Public self-booking created",
        },
      },
      slot: {
        create: {
          doctorId: doctor.id,
          startsAt,
          endsAt,
          isBlocked: false,
        },
      },
    },
    include: {
      patient: true,
      doctor: { include: { user: true, department: true } },
      clinic: true,
      department: true,
    },
  });

  await queueBookingConfirmed(visit.id);

  // Notify doctor (console / stored notification via event)
  await prisma.visitEvent.create({
    data: {
      visitId: visit.id,
      eventType: "NOTIFY",
      message: `DOCTOR_NEW_BOOKING — ${patient.fullName} token ${chosen.tokenNumber} at ${startsAt.toLocaleString()}`,
      metadataJson: JSON.stringify({
        doctorEmail: doctor.user.email,
        doctorName: doctor.user.fullName,
      }),
    },
  });
  console.log(
    `[notify:DOCTOR] to=${doctor.user.email ?? doctor.user.fullName} :: New booking token ${chosen.tokenNumber} — ${patient.fullName} at ${startsAt.toLocaleString()}`
  );

  return visit;
}

export class BookingError extends Error {
  status: number;
  constructor(message: string, status = 400) {
    super(message);
    this.status = status;
  }
}
