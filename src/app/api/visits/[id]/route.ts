import { UserRole } from "@prisma/client";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { handleApiError, jsonError, jsonOk } from "@/lib/api";

type Params = { params: Promise<{ id: string }> };

export async function GET(_req: Request, { params }: Params) {
  try {
    const session = await requireSession([
      UserRole.EMPLOYEE,
      UserRole.ADMIN,
      UserRole.DOCTOR,
      UserRole.PATIENT,
    ]);
    const { id } = await params;

    const visit = await prisma.visit.findFirst({
      where: { id, clinicId: session.clinicId ?? undefined },
      include: {
        patient: true,
        doctor: { include: { user: { select: { fullName: true } } } },
        department: true,
        events: { orderBy: { createdAt: "asc" } },
        notes: {
          where:
            session.role === UserRole.PATIENT
              ? { noteType: { in: ["STAFF", "PATIENT_SUMMARY"] } }
              : undefined,
          orderBy: { createdAt: "asc" },
        },
        notifications: { orderBy: { createdAt: "desc" } },
      },
    });

    if (!visit) return jsonError("Visit not found", 404);

    if (session.role === UserRole.PATIENT) {
      const patient = await prisma.patient.findFirst({
        where: { userId: session.id },
      });
      if (!patient || visit.patientId !== patient.id) {
        return jsonError("Forbidden", 403);
      }
      // Hide clinical notes from patients
      visit.notes = visit.notes.filter((n) => n.noteType !== "CLINICAL");
    }

    if (session.role === UserRole.DOCTOR) {
      const doctor = await prisma.doctor.findFirst({
        where: { userId: session.id },
      });
      if (!doctor || visit.doctorId !== doctor.id) {
        return jsonError("Forbidden", 403);
      }
    }

    return jsonOk({ visit });
  } catch (err) {
    return handleApiError(err);
  }
}
