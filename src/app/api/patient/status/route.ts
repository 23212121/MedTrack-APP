import { UserRole } from "@prisma/client";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { handleApiError, jsonOk } from "@/lib/api";
import { buildPatientQueueView } from "@/lib/queue";

export async function GET() {
  try {
    const session = await requireSession([UserRole.PATIENT]);

    const patient = await prisma.patient.findFirst({
      where: { userId: session.id },
    });
    if (!patient) {
      return jsonOk({
        patient: null,
        activeVisit: null,
        queue: null,
        notifications: [],
        visits: [],
      });
    }

    const visits = await prisma.visit.findMany({
      where: { patientId: patient.id },
      orderBy: { createdAt: "desc" },
      include: {
        doctor: {
          include: {
            user: { select: { fullName: true } },
          },
        },
        clinic: true,
        events: { orderBy: { createdAt: "desc" }, take: 20 },
        notifications: { orderBy: { createdAt: "desc" }, take: 30 },
      },
      take: 20,
    });

    const active =
      visits.find((v) =>
        ["CALLED", "BOOKED", "CHECKED_IN", "IN_CONSULT"].includes(v.status)
      ) ?? null;

    const queue = active ? await buildPatientQueueView(active.id) : null;

    const notifications = await prisma.notification.findMany({
      where: { patientId: patient.id },
      orderBy: { createdAt: "desc" },
      take: 40,
    });

    return jsonOk({
      patient: {
        id: patient.id,
        fullName: patient.fullName,
        phone: patient.phone,
        email: patient.email,
      },
      activeVisit: active
        ? {
            id: active.id,
            status: active.status,
            tokenNumber: active.tokenNumber,
            scheduledStart: active.scheduledStart,
            scheduledEnd: active.scheduledEnd,
            delayMinutes: active.delayMinutes,
            reason: active.reason,
            doctorName:
              active.doctor?.user.fullName ?? queue?.doctorName ?? null,
            fixedConsultMinutes:
              active.doctor?.fixedConsultMinutes ??
              active.clinic.fixedConsultMinutes,
            events: active.events,
          }
        : null,
      queue,
      notifications,
      visits: visits.map((v) => ({
        id: v.id,
        status: v.status,
        tokenNumber: v.tokenNumber,
        scheduledStart: v.scheduledStart,
        doctorName: v.doctor?.user.fullName ?? null,
        createdAt: v.createdAt,
      })),
    });
  } catch (err) {
    return handleApiError(err);
  }
}
