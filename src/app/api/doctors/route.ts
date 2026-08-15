import { UserRole } from "@prisma/client";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { handleApiError, jsonOk } from "@/lib/api";

export async function GET() {
  try {
    const session = await requireSession([
      UserRole.EMPLOYEE,
      UserRole.ADMIN,
      UserRole.DOCTOR,
    ]);

    const doctors = await prisma.doctor.findMany({
      where: { clinicId: session.clinicId!, isActive: true },
      include: {
        user: { select: { fullName: true, email: true } },
        department: true,
      },
      orderBy: { user: { fullName: "asc" } },
    });

    return jsonOk({ doctors });
  } catch (err) {
    return handleApiError(err);
  }
}
