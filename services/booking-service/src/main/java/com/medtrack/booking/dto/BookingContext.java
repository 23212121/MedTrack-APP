package com.medtrack.booking.dto;

/** Context for who is creating an appointment (hospital desk vs patient self-service). */
public record BookingContext(
    String bookedBy, Long hospitalId, String createdBy, String updatedBy) {

  public static BookingContext fromRequest(
      CreateBookingRequest req, String headerHospitalId, String headerUser) {
    String bookedBy = normalizeBookedBy(req.getBookedBy());
    Long hospitalId = resolveHospitalId(req, headerHospitalId, bookedBy);
    String actor = firstNonBlank(req.getCreatedBy(), headerUser, bookedBy);
    return new BookingContext(bookedBy, hospitalId, actor, actor);
  }

  private static String normalizeBookedBy(String raw) {
    if (raw == null || raw.isBlank()) {
      return "PATIENT";
    }
    return raw.trim().toUpperCase();
  }

  private static Long resolveHospitalId(
      CreateBookingRequest req, String headerHospitalId, String bookedBy) {
    if (req.getHospitalId() != null) {
      return req.getHospitalId();
    }
    if (headerHospitalId != null && !headerHospitalId.isBlank()) {
      try {
        return Long.parseLong(headerHospitalId.trim());
      } catch (NumberFormatException ignored) {
        // fall through
      }
    }
    return null;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v.trim();
      }
    }
    return "SYSTEM";
  }
}
