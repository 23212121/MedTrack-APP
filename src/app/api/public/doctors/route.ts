import { NextRequest } from "next/server";
import { prisma } from "@/lib/prisma";
import { handleApiError, jsonError, jsonOk } from "@/lib/api";

const DAY_NAMES = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

/** Public: doctors for a hospital, with working schedules. */
export async function GET(req: NextRequest) {
  try {
    const clinicId = req.nextUrl.searchParams.get("clinicId");
    if (!clinicId) return jsonError("clinicId is required", 400);

    const q = (req.nextUrl.searchParams.get("q") || "").trim().toLowerCase();

    const doctors = await prisma.doctor.findMany({
      where: { clinicId, isActive: true },
      include: {
        user: { select: { fullName: true } },
        department: { select: { id: true, name: true } },
        schedules: {
          orderBy: [{ dayOfWeek: "asc" }, { startTime: "asc" }],
        },
      },
      orderBy: { user: { fullName: "asc" } },
    });

    const mapped = doctors
      .map((d) => {
        const workingSummary = d.schedules.length
          ? d.schedules
              .map(
                (s) =>
                  `${DAY_NAMES[s.dayOfWeek]} ${s.startTime}–${s.endTime}`
              )
              .join(", ")
          : "No schedule set";

        return {
          id: d.id,
          name: d.user.fullName,
          specialty: d.specialty ?? d.department?.name ?? "General",
          department: d.department?.name ?? null,
          consultationFee: d.baseConsultFee,
          consultMinutes: d.avgConsultMinutes,
          workingSummary,
          schedules: d.schedules.map((s) => ({
            dayOfWeek: s.dayOfWeek,
            dayName: DAY_NAMES[s.dayOfWeek],
            startTime: s.startTime,
            endTime: s.endTime,
            slotMinutes: s.slotMinutes,
          })),
        };
      })
      .filter((d) => {
        if (!q) return true;
        return (
          d.name.toLowerCase().includes(q) ||
          d.specialty.toLowerCase().includes(q) ||
          (d.department?.toLowerCase().includes(q) ?? false)
        );
      });

    return jsonOk({ doctors: mapped });
  } catch (err) {
    return handleApiError(err);
  }
}
