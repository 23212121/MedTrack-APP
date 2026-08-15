import { NotificationChannel, UserRole } from "@prisma/client";
import { z } from "zod";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { handleApiError, jsonError, jsonOk } from "@/lib/api";
import { queueVisitNotification } from "@/lib/notifications";
import { notifyQueueDelay } from "@/lib/queue";

type Params = { params: Promise<{ id: string }> };

const bodySchema = z.object({
  minutes: z.number().int().min(5).max(120).optional().default(15),
});

const CHANNELS = [
  NotificationChannel.SMS,
  NotificationChannel.EMAIL,
  NotificationChannel.PUSH,
];

export async function POST(req: Request, { params }: Params) {
  try {
    const session = await requireSession([
      UserRole.DOCTOR,
      UserRole.ADMIN,
      UserRole.EMPLOYEE,
    ]);
    const { id } = await params;
    const body = bodySchema.parse(await req.json().catch(() => ({})));

    const visit = await prisma.visit.findFirst({
      where: { id, clinicId: session.clinicId ?? undefined },
      include: { clinic: true },
    });
    if (!visit) return jsonError("Visit not found", 404);
    if (["COMPLETED", "CANCELLED", "NO_SHOW"].includes(visit.status)) {
      return jsonError(`Cannot mark late from ${visit.status}`, 400);
    }

    if (visit.delayAlertsSent >= visit.clinic.maxDelayAlertsPerVisit) {
      return jsonError("Max delay alerts already sent for this visit", 400);
    }

    const updated = await prisma.visit.update({
      where: { id },
      data: {
        delayMinutes: { increment: body.minutes },
        delayAlertsSent: { increment: 1 },
        events: {
          create: {
            actorUserId: session.id,
            eventType: "DELAY",
            message: `Doctor running late +${body.minutes} minutes`,
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

    const estimated = updated.scheduledStart
      ? new Date(
          updated.scheduledStart.getTime() + updated.delayMinutes * 60_000
        )
      : new Date(Date.now() + updated.delayMinutes * 60_000);

    await queueVisitNotification(id, "DOCTOR_DELAYED", CHANNELS, {
      allowRepeat: true,
      extraVars: {
        delay_minutes: updated.delayMinutes,
        estimated_time: estimated.toLocaleTimeString(),
      },
    });

    // Also warn other waiting patients in the same doctor queue
    if (visit.doctorId) {
      await notifyQueueDelay(
        visit.doctorId,
        body.minutes,
        session.id,
        visit.id
      );
    }

    return jsonOk({ visit: updated });
  } catch (err) {
    return handleApiError(err);
  }
}
