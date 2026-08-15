import { NextRequest } from "next/server";
import { z } from "zod";
import { BookingError, createPublicBooking } from "@/lib/booking";
import { handleApiError, jsonError, jsonOk } from "@/lib/api";

const schema = z.object({
  clinicId: z.string().min(1),
  doctorId: z.string().min(1),
  patientName: z.string().min(2),
  phone: z.string().min(8),
  age: z.coerce.number().int().min(1).max(120).optional(),
  gender: z.enum(["MALE", "FEMALE", "OTHER", "UNKNOWN"]).optional(),
  address: z.string().optional(),
  reason: z.string().optional(),
  appointmentDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  scheduledStart: z.string().min(1),
});

/** Public: create guest appointment from doctor schedule slot. */
export async function POST(req: NextRequest) {
  try {
    const body = schema.parse(await req.json());
    const visit = await createPublicBooking(body);

    return jsonOk(
      {
        confirmation: {
          visitId: visit.id,
          patient: visit.patient.fullName,
          phone: visit.patient.phone,
          hospital: visit.clinic.name,
          doctor: visit.doctor?.user.fullName ?? "",
          specialty:
            visit.doctor?.specialty ?? visit.department?.name ?? "",
          token: visit.tokenNumber,
          appointmentTime: visit.scheduledStart?.toISOString() ?? null,
          date: body.appointmentDate,
          fee: visit.baseFee,
          currency: visit.feeCurrency ?? "INR",
          status: visit.status,
        },
      },
      201
    );
  } catch (err) {
    if (err instanceof BookingError) {
      return jsonError(err.message, err.status);
    }
    return handleApiError(err);
  }
}
