package com.medtrack.booking.service;

import com.medtrack.booking.domain.BookingEntity;
import com.medtrack.visit.service.VisitAppService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Mirrors bookings into visits without rolling back the appointment transaction. */
@Service
public class BookingVisitSyncService {
  private final VisitAppService visitAppService;
  private final AppointmentEnrichmentService enrichment;

  public BookingVisitSyncService(
      VisitAppService visitAppService, AppointmentEnrichmentService enrichment) {
    this.visitAppService = visitAppService;
    this.enrichment = enrichment;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void syncFromBooking(BookingEntity b) {
    try {
      String reason = b.getReason() == null ? "" : b.getReason();
      if (b.getAddress() != null && !b.getAddress().isBlank()) {
        reason =
            reason.isBlank() ? "Address: " + b.getAddress() : reason + " | Address: " + b.getAddress();
      }
      if (b.getPatientAge() != null) {
        reason =
            reason.isBlank()
                ? "Age: " + b.getPatientAge()
                : reason + " | Age: " + b.getPatientAge();
      }

      Map<String, Object> body = new HashMap<>();
      body.put("clinicId", String.valueOf(b.getHospitalId()));
      body.put("hospitalId", b.getHospitalId());
      body.put("patientName", b.getPatientName());
      body.put("patientPhone", b.getPatientPhone());
      body.put("doctorId", b.getDoctorId());
      body.put("doctorName", enrichment.doctorName(b.getDoctorId()));
      body.put("status", "BOOKED");
      body.put("reason", reason.isBlank() ? null : reason);
      body.put("scheduledStart", b.getAppointmentTime().toString());
      if (b.getTokenNumber() != null) {
        body.put("tokenNumber", b.getTokenNumber());
      }
      // Confirmation SMS/email is sent by BookingNotificationService — avoid duplicate
      body.put("skipBookingNotify", true);
      Double fee = enrichment.consultationFee(b.getDoctorId());
      if (fee != null) {
        body.put("baseFee", fee);
      }

      visitAppService.create(body);
    } catch (Exception ex) {
      System.err.printf(
          "[booking] visit sync failed (appointment still saved): %s%n", ex.getMessage());
    }
  }
}
