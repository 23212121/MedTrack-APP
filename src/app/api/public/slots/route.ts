import { NextRequest } from "next/server";
import { getAvailableSlots } from "@/lib/booking";
import { handleApiError, jsonError, jsonOk } from "@/lib/api";

/** Public: available appointment slots from doctor_schedules for a date. */
export async function GET(req: NextRequest) {
  try {
    const doctorId = req.nextUrl.searchParams.get("doctorId");
    const date = req.nextUrl.searchParams.get("date");

    if (!doctorId) return jsonError("doctorId is required", 400);
    if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      return jsonError("date is required as YYYY-MM-DD", 400);
    }

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const selected = new Date(`${date}T12:00:00`);
    if (selected < today) {
      return jsonError("Cannot book past dates", 400);
    }

    const result = await getAvailableSlots(doctorId, date);
    return jsonOk({
      date,
      workingDay: result.workingDay,
      schedules: result.schedules,
      slots: result.slots,
      nextSlot: result.slots[0] ?? null,
    });
  } catch (err) {
    return handleApiError(err);
  }
}
