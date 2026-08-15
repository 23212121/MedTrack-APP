import { NotificationChannel, UserRole } from "@prisma/client";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { handleApiError, jsonError, jsonOk } from "@/lib/api";
import { queueVisitNotification } from "@/lib/notifications";
import { notifyQueueDelay } from "@/lib/queue";

type Params = { params: Promise<{ id: string }> };

const CHANNELS = [
  NotificationChannel.SMS,
  NotificationChannel.EMAIL,
  NotificationChannel.PUSH,
];

export async function POST(_req: Request, { params }: Params) {
  try {
    const session = await requireSession([
      UserRole.DOCTOR,
      UserRole.ADMIN,
      UserRole.EMPLOYEE,
    ]);
    const { id } = await params;

    const visit = await prisma.visit.findFirst({
      where: { id, clinicId: session.clinicId ?? undefined },
      include: { clinic: true, doctor: true },
    });
    if (!visit) return jsonError("Visit not found", 404);
    if (!["CHECKED_IN", "BOOKED"].includes(visit.status)) {
      return jsonError(`Cannot start consult from ${visit.status}`, 400);
    }

    // If another consult is still open and overtime, warn the next patients
    if (visit.doctorId) {
      const open = await prisma.visit.findFirst({
        where: {
          doctorId: visit.doctorId,
          status: "IN_CONSULT",
          id: { not: id },
        },
      });
      if (open?.actualStart) {
        const fixed =
          visit.doctor?.fixedConsultMinutes ??
          visit.clinic.fixedConsultMinutes ??
          15;
        const elapsed = Math.round(
          (Date.now() - open.actualStart.getTime()) / 60_000
        );
        if (elapsed > fixed) {
          await notifyQueueDelay(
            visit.doctorId,
            elapsed - fixed,
            session.id,
            open.id
          );
        }
      }
    }

    const updated = await prisma.visit.update({
      where: { id },
      data: {
        status: "IN_CONSULT",
        actualStart: new Date(),
        events: {
          create: {
            actorUserId: session.id,
            eventType: "STATUS_CHANGE",
            fromStatus: visit.status,
            toStatus: "IN_CONSULT",
            message: "Consult started",
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

    await queueVisitNotification(id, "CHECKUP_STARTED", CHANNELS);

    return jsonOk({ visit: updated });
  } catch (err) {
    return handleApiError(err);
  }
}
