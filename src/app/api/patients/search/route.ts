import { NextRequest } from "next/server";
import { UserRole } from "@prisma/client";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { handleApiError, jsonError, jsonOk } from "@/lib/api";

export async function GET(req: NextRequest) {
  try {
    const session = await requireSession([
      UserRole.EMPLOYEE,
      UserRole.ADMIN,
      UserRole.DOCTOR,
    ]);

    const phone = req.nextUrl.searchParams.get("phone")?.replace(/\D/g, "");
    if (!phone || phone.length < 8) {
      return jsonError("Query param phone is required (min 8 digits)");
    }

    const clinicId = session.clinicId!;
    const patients = await prisma.patient.findMany({
      where: {
        clinicId,
        phone: { contains: phone },
      },
      orderBy: { fullName: "asc" },
      include: {
        visits: {
          orderBy: { createdAt: "desc" },
          take: 3,
          select: {
            id: true,
            status: true,
            scheduledStart: true,
            createdAt: true,
          },
        },
      },
    });

    return jsonOk({ phone, count: patients.length, patients });
  } catch (err) {
    return handleApiError(err);
  }
}
