import { VisitStatus } from "@prisma/client";
import { prisma } from "./prisma";

function ageFromDob(dob: Date | null | undefined): number | null {
  if (!dob) return null;
  const now = new Date();
  let age = now.getFullYear() - dob.getFullYear();
  const m = now.getMonth() - dob.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < dob.getDate())) age -= 1;
  return age;
}

function statusLabel(status: VisitStatus): string {
  switch (status) {
    case "BOOKED":
    case "CHECKED_IN":
    case "CALLED":
      return "Waiting";
    case "IN_CONSULT":
      return "In Progress";
    case "COMPLETED":
      return "Completed";
    case "CANCELLED":
    case "NO_SHOW":
      return "Cancelled";
    default:
      return status;
  }
}

export async function loadDoctorDashboard(userId: string, clinicId: string | null) {
  const doctor =
    (await prisma.doctor.findFirst({
      where: { userId },
      include: { user: { select: { fullName: true } } },
    })) ||
    (clinicId
      ? await prisma.doctor.findFirst({
          where: { clinicId },
          include: { user: { select: { fullName: true } } },
        })
      : null);

  if (!doctor) {
    return {
      doctorName: "Doctor",
      totalPatients: 0,
      todayAppointments: 0,
      pendingSuggestions: 0,
      upcoming: [] as {
        time: string;
        name: string;
        reason: string;
      }[],
      recent: [] as {
        name: string;
        age: string;
        disease: string;
        status: string;
      }[],
    };
  }

  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const end = new Date();
  end.setHours(23, 59, 59, 999);

  const [totalPatients, todayAppointments, upcomingVisits, recentVisits] =
    await Promise.all([
      prisma.visit.findMany({
        where: { doctorId: doctor.id },
        select: { patientId: true },
        distinct: ["patientId"],
      }),
      prisma.visit.count({
        where: {
          doctorId: doctor.id,
          OR: [
            { scheduledStart: { gte: start, lte: end } },
            { createdAt: { gte: start, lte: end }, scheduledStart: null },
          ],
        },
      }),
      prisma.visit.findMany({
        where: {
          doctorId: doctor.id,
          status: { in: ["BOOKED", "CHECKED_IN", "CALLED", "IN_CONSULT"] },
        },
        orderBy: [{ scheduledStart: "asc" }, { tokenNumber: "asc" }],
        take: 6,
        include: { patient: { select: { fullName: true } } },
      }),
      prisma.visit.findMany({
        where: { doctorId: doctor.id },
        orderBy: { updatedAt: "desc" },
        take: 8,
        include: {
          patient: { select: { fullName: true, dateOfBirth: true } },
        },
      }),
    ]);

  const pendingSuggestions = upcomingVisits.filter(
    (v) => v.status === "BOOKED" || v.status === "CHECKED_IN"
  ).length;

  const upcoming = upcomingVisits.map((v) => {
    const t = v.scheduledStart
      ? v.scheduledStart.toLocaleTimeString([], {
          hour: "2-digit",
          minute: "2-digit",
        })
      : v.tokenNumber
        ? `Token #${v.tokenNumber}`
        : "—";
    return {
      time: t,
      name: v.patient.fullName,
      reason: v.reason || "Consultation",
    };
  });

  const recent = recentVisits.map((v) => {
    const age = ageFromDob(v.patient.dateOfBirth);
    return {
      name: v.patient.fullName,
      age: age == null ? "—" : String(age),
      disease: v.reason || "General",
      status: statusLabel(v.status),
    };
  });

  return {
    doctorName: doctor.user.fullName,
    totalPatients: totalPatients.length,
    todayAppointments,
    pendingSuggestions,
    upcoming,
    recent,
  };
}
