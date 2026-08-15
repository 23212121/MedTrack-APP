import { prisma } from "@/lib/prisma";
import { handleApiError, jsonOk } from "@/lib/api";

/** Public: list hospitals/clinics for guest booking. */
export async function GET() {
  try {
    const clinics = await prisma.clinic.findMany({
      orderBy: { name: "asc" },
      select: {
        id: true,
        name: true,
        timezone: true,
        _count: { select: { doctors: true } },
      },
    });

    return jsonOk({
      clinics: clinics.map((c) => ({
        id: c.id,
        name: c.name,
        timezone: c.timezone,
        doctorCount: c._count.doctors,
      })),
    });
  } catch (err) {
    return handleApiError(err);
  }
}
