package com.medtrack.booking.service;

import com.medtrack.booking.domain.BookingEntity;
import com.medtrack.booking.domain.PatientEntity;
import com.medtrack.booking.domain.UserDetailsEntity;
import com.medtrack.booking.repo.PatientRepository;
import com.medtrack.booking.repo.UserDetailsRepository;
import com.medtrack.common.dto.NotifyRequest;
import com.medtrack.notify.service.NotificationAppService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Sends SMS (and email when available) after an appointment is booked. */
@Service
public class BookingNotificationService {
  private static final Logger log = LoggerFactory.getLogger(BookingNotificationService.class);
  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("h:mm a").withZone(ZONE);
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

  private final NotificationAppService notificationAppService;
  private final AppointmentEnrichmentService enrichment;
  private final PatientRepository patientRepo;
  private final UserDetailsRepository userDetailsRepo;

  public BookingNotificationService(
      NotificationAppService notificationAppService,
      AppointmentEnrichmentService enrichment,
      PatientRepository patientRepo,
      UserDetailsRepository userDetailsRepo) {
    this.notificationAppService = notificationAppService;
    this.enrichment = enrichment;
    this.patientRepo = patientRepo;
    this.userDetailsRepo = userDetailsRepo;
  }

  public void notifyAppointmentBooked(BookingEntity booking, String patientEmailFromRequest) {
    if (booking == null) {
      return;
    }
    try {
      String phone = normalizePhone(booking.getPatientPhone());
      String email =
          firstNonBlank(
              patientEmailFromRequest, emailFromPatientRecord(phone), emailFromUserDetails(phone));
      String doctor = enrichment.doctorName(booking.getDoctorId());
      String clinic = enrichment.hospitalName(booking.getHospitalId());
      if (clinic == null || clinic.isBlank()) {
        clinic = "MedTrack Clinic";
      }
      String when = formatWhen(booking);
      String token =
          booking.getTokenNumber() == null ? "—" : String.valueOf(booking.getTokenNumber());

      Map<String, String> extra = new HashMap<>();
      extra.put("appointmentDate", booking.getAppointmentDate().toString());
      extra.put("whenDisplay", when);

      NotifyRequest req =
          new NotifyRequest(
              String.valueOf(booking.getHospitalId()),
              booking.getId(),
              phone,
              "BOOKING_CONFIRMED",
              booking.getPatientName(),
              phone.isBlank() ? null : phone,
              email,
              phone != null && !phone.isBlank(),
              email != null && !email.isBlank(),
              doctor,
              clinic,
              when,
              token,
              null,
              "BOOKED",
              null,
              enrichment.consultationFee(booking.getDoctorId()),
              "INR",
              extra);

      Map<String, Object> result = notificationAppService.send(req);
      log.info(
          "[booking-notify] BOOKING_CONFIRMED booking={} phone={} email={} result={}",
          booking.getId(),
          phone,
          email == null || email.isBlank() ? "(none)" : email,
          result.get("eventCode"));
    } catch (Exception ex) {
      log.warn(
          "[booking-notify] failed for booking {}: {}", booking.getId(), ex.getMessage());
    }
  }

  private String emailFromPatientRecord(String phone) {
    if (phone == null || phone.isBlank()) {
      return null;
    }
    return patientRepo
        .findByPhone(phone)
        .map(PatientEntity::getEmail)
        .filter(e -> e != null && !e.isBlank())
        .orElse(null);
  }

  private String emailFromUserDetails(String phone) {
    if (phone == null || phone.isBlank()) {
      return null;
    }
    return userDetailsRepo
        .findByPhone(phone)
        .map(UserDetailsEntity::getEmail)
        .filter(e -> e != null && !e.isBlank())
        .orElse(null);
  }

  private static String formatWhen(BookingEntity booking) {
    String date =
        booking.getAppointmentDate() == null
            ? ""
            : DATE_FMT.format(booking.getAppointmentDate());
    String time =
        booking.getAppointmentTime() == null
            ? ""
            : TIME_FMT.format(booking.getAppointmentTime());
    if (!date.isBlank() && !time.isBlank()) {
      return date + " at " + time;
    }
    return !date.isBlank() ? date : (time.isBlank() ? "TBD" : time);
  }

  private static String normalizePhone(String raw) {
    if (raw == null) return "";
    return raw.replaceAll("\\D", "");
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v.trim();
      }
    }
    return null;
  }
}
