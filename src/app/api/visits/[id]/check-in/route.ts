import { NotificationChannel, UserRole } from "@prisma/client";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { handleApiError, jsonError, jsonOk } from "@/lib/api";
import { queueVisitNotification } from "@/lib/notifications";
import { buildPatientQueueView } from "@/lib/queue";

type Params = { params: Promise<{ id: string }> };

const CHANNELS = [
  NotificationChannel.SMS,
  NotificationChannel.EMAIL,
  NotificationChannel.PUSH,
];

export async function POST(_req: Request, { params }: Params) {
  try {
    const session = await requireSession([UserRole.EMPLOYEE, UserRole.ADMIN]);
    const { id } = await params;

    const visit = await prisma.visit.findFirst({
      where: { id, clinicId: session.clinicId ?? undefined },
      include: { clinic: true, doctor: true },
    });
    if (!visit) return jsonError("Visit not found", 404);
    if (!["BOOKED", "CALLED"].includes(visit.status)) {
      return jsonError(`Cannot check in from ${visit.status}`, 400);
    }

    const dayStart = new Date();
    dayStart.setHours(0, 0, 0, 0);
    const checkedInToday = await prisma.visit.count({
      where: {
        doctorId: visit.doctorId ?? undefined,
        checkedInAt: { gte: dayStart },
        status: { in: ["CHECKED_IN", "IN_CONSULT", "COMPLETED"] },
      },
    });

    const tokenNumber = checkedInToday + 1;
    const updated = await prisma.visit.update({
      where: { id },
      data: {
        status: "CHECKED_IN",
        checkedInAt: new Date(),
        tokenNumber,
        events: {
          create: {
            actorUserId: session.id,
            eventType: "STATUS_CHANGE",
            fromStatus: visit.status,
            toStatus: "CHECKED_IN",
            message: `Checked in, token=${tokenNumber}`,
          },
        },
      },
      include: {
        patient: true,
        doctor: { include: { user: { select: { fullName: true } } } },
        events: { orderBy: { createdAt: "asc" } },
        notifications: true,
      },
    });

    const queueView = await buildPatientQueueView(updated.id);
    await queueVisitNotification(updated.id, "CHECKED_IN", CHANNELS, {
      extraVars: {
        fixed_minutes: queueView?.fixedConsultMinutes ?? 15,
        estimated_time: queueView?.estimatedConsultAt
          ? queueView.estimatedConsultAt.toLocaleTimeString()
          : "soon",
        queue_position: queueView?.queuePosition ?? tokenNumber,
        patients_ahead: queueView?.patientsAhead ?? 0,
      },
    });

    return jsonOk({ visit: updated, queue: queueView });
  } catch (err) {
    return handleApiError(err);
  }
}
