import { UserRole } from "@prisma/client";
import { z } from "zod";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { handleApiError, jsonError, jsonOk } from "@/lib/api";

export async function GET(req: Request) {
  try {
    await requireSession([
      UserRole.ADMIN,
      UserRole.EMPLOYEE,
      UserRole.DOCTOR,
    ]);
    const doctorId = new URL(req.url).searchParams.get("doctorId");
    if (!doctorId) return jsonError("doctorId required", 400);

    const schedules = await prisma.doctorSchedule.findMany({
      where: { doctorId },
      orderBy: [{ dayOfWeek: "asc" }, { startTime: "asc" }],
    });
    const availability = await prisma.doctorAvailability.findMany({
      where: { doctorId },
      orderBy: { startsAt: "asc" },
      take: 100,
    });
    return jsonOk({ schedules, availability });
  } catch (err) {
    return handleApiError(err);
  }
}

const putSchema = z.object({
  doctorId: z.string().min(1),
  schedules: z.array(
    z.object({
      dayOfWeek: z.number().int().min(0).max(6),
      startTime: z.string(),
      endTime: z.string(),
      slotMinutes: z.number().int().min(5).max(60).optional(),
    })
  ),
});

export async function PUT(req: Request) {
  try {
    await requireSession([UserRole.ADMIN]);
    const body = putSchema.parse(await req.json());

    await prisma.doctorSchedule.deleteMany({ where: { doctorId: body.doctorId } });
    await prisma.doctorSchedule.createMany({
      data: body.schedules.map((s) => ({
        doctorId: body.doctorId,
        dayOfWeek: s.dayOfWeek,
        startTime: s.startTime,
        endTime: s.endTime,
        slotMinutes: s.slotMinutes ?? 15,
      })),
    });

    const schedules = await prisma.doctorSchedule.findMany({
      where: { doctorId: body.doctorId },
      orderBy: [{ dayOfWeek: "asc" }, { startTime: "asc" }],
    });
    return jsonOk({ schedules });
  } catch (err) {
    return handleApiError(err);
  }
}
