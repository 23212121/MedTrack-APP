import { NotificationChannel, UserRole } from "@prisma/client";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { handleApiError, jsonError, jsonOk } from "@/lib/api";
import { queueVisitNotification } from "@/lib/notifications";
import {
  advanceQueueAfterComplete,
  notifyQueueDelay,
} from "@/lib/queue";

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
      include: { doctor: true, clinic: true },
    });
    if (!visit) return jsonError("Visit not found", 404);
    if (!["IN_CONSULT", "CHECKED_IN"].includes(visit.status)) {
      return jsonError(`Cannot complete from ${visit.status}`, 400);
    }

    const end = new Date();
    const start = visit.actualStart ?? new Date(end.getTime() - 15 * 60_000);
    const actualMinutes = Math.max(
      1,
      Math.round((end.getTime() - start.getTime()) / 60_000)
    );

    const fixed =
      visit.doctor?.fixedConsultMinutes ??
      visit.clinic.fixedConsultMinutes ??
      15;
    const base = visit.doctor?.baseConsultFee ?? 500;
    const otAmount =
      visit.doctor?.overtimeFeeAmount ?? visit.clinic.overtimeFeeAmount;
    const block = visit.clinic.overtimeFeePerBlockMinutes;
    const overtimeMinutes = Math.max(0, actualMinutes - fixed);
    const blocks =
      overtimeMinutes === 0 ? 0 : Math.ceil(overtimeMinutes / block);
    const overtimeFee = blocks * otAmount;
    const totalFee = base + overtimeFee;

    // If this consult ran long, warn next waiting patients before advancing
    if (visit.doctorId && overtimeMinutes > 0) {
      await notifyQueueDelay(
        visit.doctorId,
        overtimeMinutes,
        session.id,
        visit.id
      );
    }

    await prisma.visit.update({
      where: { id },
      data: {
        status: "COMPLETED",
        actualStart: start,
        actualEnd: end,
        baseFee: base,
        overtimeMinutes,
        overtimeFee,
        totalFee,
        feeCurrency: visit.clinic.overtimeFeeCurrency,
        events: {
          create: {
            actorUserId: session.id,
            eventType: "STATUS_CHANGE",
            fromStatus: visit.status,
            toStatus: "COMPLETED",
            message: `Completed. overtime=${overtimeMinutes}m fee=${totalFee}`,
          },
        },
      },
    });

    await prisma.visitFeeCharge.createMany({
      data: [
        {
          visitId: id,
          chargeType: "BASE",
          description: `Base consult (${fixed} min)`,
          amount: base,
          currency: visit.clinic.overtimeFeeCurrency,
        },
        ...(overtimeFee > 0
          ? [
              {
                visitId: id,
                chargeType: "OVERTIME",
                description: `Overtime ${overtimeMinutes} min`,
                amount: overtimeFee,
                currency: visit.clinic.overtimeFeeCurrency,
                minutesOver: overtimeMinutes,
              },
            ]
          : []),
      ],
    });

    await queueVisitNotification(id, "VISIT_COMPLETED", CHANNELS);
    if (overtimeFee > 0) {
      await queueVisitNotification(id, "OVERTIME_FEE", CHANNELS, {
        allowRepeat: true,
      });
    }

    // Next token becomes current; notify that patient + refresh timeline for others
    await advanceQueueAfterComplete(id, session.id);

    return jsonOk({
      visit: await prisma.visit.findUnique({
        where: { id },
        include: {
          patient: true,
          doctor: { include: { user: { select: { fullName: true } } } },
          events: { orderBy: { createdAt: "asc" } },
          notifications: true,
          feeCharges: true,
        },
      }),
    });
  } catch (err) {
    return handleApiError(err);
  }
}
