package com.medtrack.booking.service;

import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.domain.BookingEntity;
import com.medtrack.booking.domain.LabBookingEntity;
import com.medtrack.booking.domain.PatientEntity;
import com.medtrack.booking.domain.UserDetailsEntity;
import com.medtrack.booking.event.DocumentUploadedEvent;
import com.medtrack.booking.event.PaymentCompletedEvent;
import com.medtrack.booking.repo.AppointmentRepository;
import com.medtrack.booking.repo.BookingRepository;
import com.medtrack.booking.repo.PatientRepository;
import com.medtrack.booking.repo.UserDetailsRepository;
import com.medtrack.common.dto.NotifyRequest;
import com.medtrack.notify.service.NotificationAppService;
import com.medtrack.visit.domain.VisitEntity;
import com.medtrack.visit.repo.VisitRepository;
import com.medtrack.visit.service.VisitAppService;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
  private final AppointmentRepository appointmentRepo;
  private final VisitRepository visitRepo;
  private final VisitAppService visitAppService;
  private final BookingRepository bookingRepo;
  private final int avgConsultationMinutes;
  private final String publicWebUrl;

  public BookingNotificationService(
      NotificationAppService notificationAppService,
      AppointmentEnrichmentService enrichment,
      PatientRepository patientRepo,
      UserDetailsRepository userDetailsRepo,
      AppointmentRepository appointmentRepo,
      VisitRepository visitRepo,
      VisitAppService visitAppService,
      BookingRepository bookingRepo,
      @Value("${medtrack.avg-consultation-minutes:10}") int avgConsultationMinutes,
      @Value("${medtrack.public-web-url:http://localhost:5173}") String publicWebUrl) {
    this.notificationAppService = notificationAppService;
    this.enrichment = enrichment;
    this.patientRepo = patientRepo;
    this.userDetailsRepo = userDetailsRepo;
    this.appointmentRepo = appointmentRepo;
    this.visitRepo = visitRepo;
    this.visitAppService = visitAppService;
    this.bookingRepo = bookingRepo;
    this.avgConsultationMinutes = Math.max(1, avgConsultationMinutes);
    this.publicWebUrl = publicWebUrl;
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
        clinic = "Swasnira";
      }
      String when = formatWhen(booking);
      String token =
          booking.getTokenNumber() == null ? "—" : String.valueOf(booking.getTokenNumber());
      SlotEstimate slot = estimateAppointmentSlot(booking);

      Map<String, String> extra = new HashMap<>();
      extra.put("appointmentDate", booking.getAppointmentDate().toString());
      extra.put("whenDisplay", when);
      extra.put("patientsOnSlot", String.valueOf(slot.patientsOnSlot()));
      extra.put("expectedTime", slot.expectedDisplay());
      extra.put("trackUrl", trackUrl("/track?phone=" + phone));

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

  /** SMS after a lab test is booked: slot size, token, calculated time, and a tracking link. */
  public void notifyLabBooked(LabBookingEntity booking) {
    if (booking == null) return;
    try {
      String phone = normalizePhone(booking.getPatientPhone());
      String email = firstNonBlank(emailFromPatientRecord(phone), emailFromUserDetails(phone));
      String clinic = enrichment.hospitalName(booking.getHospitalId());
      if (clinic == null || clinic.isBlank()) clinic = "Swasnira";
      String token =
          booking.getTokenNumber() == null ? "—" : String.valueOf(booking.getTokenNumber());
      int patients = booking.getTokenNumber() == null ? 1 : booking.getTokenNumber();
      String expected = formatInstant(booking.getExpectedVisitAt());
      String mode =
          "HOME_COLLECTION".equalsIgnoreCase(booking.getMode()) ? "Home collection" : "In lab";
      Map<String, String> extra = new HashMap<>();
      extra.put("patientsOnSlot", String.valueOf(patients));
      extra.put("expectedTime", expected);
      extra.put("trackUrl", trackUrl("/patient/lab-booking"));
      extra.put("bookingNumber", booking.getBookingNumber() == null ? "" : booking.getBookingNumber());
      extra.put("modeLabel", mode);

      NotifyRequest req =
          new NotifyRequest(
              booking.getHospitalId() == null ? "10001" : String.valueOf(booking.getHospitalId()),
              booking.getId(),
              phone,
              "LAB_BOOKING_CONFIRMED",
              booking.getPatientName(),
              phone.isBlank() ? null : phone,
              email,
              !phone.isBlank(),
              email != null && !email.isBlank(),
              mode,
              clinic,
              expected,
              token,
              null,
              "BOOKED",
              null,
              null,
              null,
              extra);
      Map<String, Object> result = notificationAppService.send(req);
      log.info(
          "[booking-notify] LAB_BOOKING_CONFIRMED booking={} phone={} patients={} token={} result={}",
          booking.getId(),
          phone,
          patients,
          token,
          result.get("eventCode"));
    } catch (Exception ex) {
      log.warn("[booking-notify] lab sms failed for {}: {}", booking.getId(), ex.getMessage());
    }
  }

  public void notifyPaymentCompleted(PaymentCompletedEvent event) {
    if (event == null || event.appointmentId() == null) return;
    try {
      var appt = appointmentRepo.findById(event.appointmentId()).orElse(null);
      if (appt == null) return;
      String phone = normalizePhone(appt.getPhoneNumber());
      String email = emailFromPatientRecord(phone);
      if (email == null) email = emailFromUserDetails(phone);
      String doctor = enrichment.doctorName(appt.getDoctorId());
      String clinic = enrichment.hospitalName(appt.getHospitalId());
      if (clinic == null || clinic.isBlank()) clinic = "MedTrack Clinic";
      String token = appt.getTokenNumber() == null ? "—" : String.valueOf(appt.getTokenNumber());
      Map<String, String> extra = new HashMap<>();
      extra.put("paymentId", event.paymentId());
      extra.put("transactionId", event.transactionId());
      extra.put("amount", String.valueOf(event.amount()));
      extra.put("appointmentId", event.appointmentId());

      NotifyRequest req =
          new NotifyRequest(
              String.valueOf(appt.getHospitalId()),
              event.appointmentId(),
              phone,
              "PAYMENT_COMPLETED",
              appt.getPatientName(),
              phone.isBlank() ? null : phone,
              email,
              phone != null && !phone.isBlank(),
              email != null && !email.isBlank(),
              doctor,
              clinic,
              null,
              token,
              null,
              "PAID",
              null,
              event.amount(),
              event.currency() == null ? "INR" : event.currency(),
              extra);

      Map<String, Object> result = notificationAppService.send(req);
      log.info(
          "[booking-notify] PAYMENT_COMPLETED appointment={} txn={} result={}",
          event.appointmentId(),
          event.transactionId(),
          result.get("eventCode"));
    } catch (Exception ex) {
      log.warn(
          "[booking-notify] payment notify failed for {}: {}",
          event.appointmentId(),
          ex.getMessage());
    }
  }

  /** SMS + email + in-app when files are attached for a patient who has a booked appointment. */
  public void notifyDocumentAttached(DocumentUploadedEvent event) {
    if (event == null) return;
    String phone = normalizePhone(event.phone());
    AppointmentEntity appt =
        phone.isBlank() ? null : latestActiveAppointment(phone, event.hospitalId());
    try {
      String email =
          phone.isBlank()
              ? null
              : firstNonBlank(emailFromPatientRecord(phone), emailFromUserDetails(phone));
      String doctor = appt != null ? enrichment.doctorName(appt.getDoctorId()) : "";
      String clinic = enrichment.hospitalName(event.hospitalId() != null ? event.hospitalId() : (appt == null ? null : appt.getHospitalId()));
      if (clinic == null || clinic.isBlank()) clinic = "MedTrack Clinic";
      String token =
          appt == null || appt.getTokenNumber() == null ? "—" : String.valueOf(appt.getTokenNumber());
      String when = appt == null ? "" : formatAppointmentWhen(appt);
      int count = Math.max(1, event.fileCount());
      Map<String, String> extra = new HashMap<>();
      extra.put("fileCount", String.valueOf(count));
      extra.put("fileNames", event.fileNames() == null ? "" : event.fileNames());
      extra.put("documentType", event.documentType() == null ? "" : event.documentType());
      extra.put("appointmentId", appt == null ? "" : appt.getId());
      extra.put("documentId", event.documentId());
      String visitId = event.documentId();
      if (appt != null) {
        String resolved = resolveVisitId(appt, phone);
        if (resolved != null && !resolved.isBlank()) visitId = resolved;
      }

      NotifyRequest req =
          new NotifyRequest(
              event.hospitalId() == null ? "10001" : String.valueOf(event.hospitalId()),
              visitId,
              phone.isBlank() ? null : phone,
              "DOCUMENT_ATTACHED",
              firstNonBlank(event.patientName(), appt == null ? null : appt.getPatientName()),
              phone.isBlank() ? null : phone,
              email,
              !phone.isBlank(),
              true,
              doctor,
              clinic,
              when,
              token,
              null,
              appt == null ? "UPLOADED" : appt.getStatus(),
              null,
              null,
              null,
              extra);

      Map<String, Object> result = notificationAppService.send(req);
      log.info(
          "[booking-notify] DOCUMENT_ATTACHED doc={} appt={} phone={} email={} files={} result={}",
          event.documentId(),
          appt == null ? "(none)" : appt.getId(),
          phone.isBlank() ? "(none)" : phone,
          email == null || email.isBlank() ? "(none)" : email,
          count,
          result.get("eventCode"));
    } catch (Exception ex) {
      log.warn(
          "[booking-notify] document notify failed for {}: {}",
          event.documentId(),
          ex.getMessage());
    }
  }

  private AppointmentEntity latestActiveAppointment(String phone, Long hospitalId) {
    AppointmentEntity found = findActiveAppointment(phone, hospitalId);
    if (found != null) return found;
    if (phone.length() == 12 && phone.startsWith("91")) {
      return findActiveAppointment(phone.substring(2), hospitalId);
    }
    if (phone.length() == 10) {
      return findActiveAppointment("91" + phone, hospitalId);
    }
    return null;
  }

  private AppointmentEntity findActiveAppointment(String phone, Long hospitalId) {
    AppointmentEntity any = null;
    for (AppointmentEntity a : appointmentRepo.findByPhoneNumberOrderByCreatedDateDesc(phone)) {
      String st = a.getStatus() == null ? "" : a.getStatus().trim().toUpperCase();
      if (st.contains("CANCEL")) continue;
      if (hospitalId != null && hospitalId > 0 && hospitalId.equals(a.getHospitalId())) {
        return a;
      }
      if (any == null) any = a;
    }
    return any;
  }

  private String resolveVisitId(AppointmentEntity appt, String phone) {
    VisitEntity picked = null;
    for (VisitEntity v : visitRepo.findByPatientPhoneContainingOrderByCreatedAtDesc(phone)) {
      String st = v.getStatus() == null ? "" : v.getStatus().trim().toUpperCase();
      if (st.contains("CANCEL")) continue;
      if (appt.getHospitalId() != null
          && v.getHospitalId() != null
          && appt.getHospitalId().equals(v.getHospitalId())
          && appt.getDoctorId() != null
          && appt.getDoctorId().equals(v.getDoctorId())) {
        return v.getId();
      }
      if (picked == null) picked = v;
    }
    return picked == null ? createVisitForNotify(appt, phone) : picked.getId();
  }

  private String createVisitForNotify(AppointmentEntity appt, String phone) {
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("clinicId", String.valueOf(appt.getHospitalId()));
      body.put("hospitalId", appt.getHospitalId());
      body.put("patientName", appt.getPatientName());
      body.put("patientPhone", phone);
      body.put("doctorId", appt.getDoctorId());
      body.put("doctorName", enrichment.doctorName(appt.getDoctorId()));
      body.put("status", appt.getStatus() == null || appt.getStatus().isBlank() ? "BOOKED" : appt.getStatus());
      body.put("skipBookingNotify", true);
      if (appt.getTokenNumber() != null) {
        body.put("tokenNumber", appt.getTokenNumber());
      }
      if (appt.getAppointmentTime() != null) {
        body.put("scheduledStart", appt.getAppointmentTime().toString());
      }
      if (appt.getReason() != null && !appt.getReason().isBlank()) {
        body.put("reason", appt.getReason());
      }
      return visitAppService.create(body).getId();
    } catch (Exception ex) {
      log.warn("[booking-notify] could not create visit for document notify: {}", ex.getMessage());
      return null;
    }
  }

  private static String formatAppointmentWhen(AppointmentEntity appt) {
    String date =
        appt.getAppointmentDate() == null ? "" : DATE_FMT.format(appt.getAppointmentDate());
      String time =
        appt.getAppointmentTime() == null ? "" : TIME_FMT.format(appt.getAppointmentTime());
    if (!date.isBlank() && !time.isBlank()) return date + " at " + time;
    return !date.isBlank() ? date : (time.isBlank() ? "TBD" : time);
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

  private SlotEstimate estimateAppointmentSlot(BookingEntity booking) {
    int patientsOnSlot = 1;
    int position = 1;
    try {
      List<BookingEntity> day =
          bookingRepo.findByDoctorIdAndAppointmentDateOrderByAppointmentTimeAsc(
              booking.getDoctorId(), booking.getAppointmentDate());
      Instant slotKey = truncateMinute(booking.getAppointmentTime());
      List<BookingEntity> onSlot =
          day.stream()
              .filter(row -> row.getStatus() == null || !"CANCELLED".equalsIgnoreCase(row.getStatus()))
              .filter(row -> slotKey != null && slotKey.equals(truncateMinute(row.getAppointmentTime())))
              .sorted(
                  Comparator.comparingInt(
                          (BookingEntity row) ->
                              row.getTokenNumber() == null ? Integer.MAX_VALUE : row.getTokenNumber())
                      .thenComparing(
                          row -> row.getCreatedAt() == null ? Instant.EPOCH : row.getCreatedAt()))
              .toList();
      if (!onSlot.isEmpty()) {
        patientsOnSlot = onSlot.size();
        for (int i = 0; i < onSlot.size(); i++) {
          if (booking.getId() != null && booking.getId().equals(onSlot.get(i).getId())) {
            position = i + 1;
            break;
          }
        }
      }
    } catch (Exception ex) {
      log.warn("[booking-notify] slot count failed for {}: {}", booking.getId(), ex.getMessage());
    }
    Instant start = booking.getAppointmentTime();
    Instant expected =
        start == null
            ? null
            : start.plus(Duration.ofMinutes((long) (position - 1) * avgConsultationMinutes));
    String display = expected == null ? formatWhen(booking) : formatInstant(expected);
    return new SlotEstimate(patientsOnSlot, display);
  }

  private String trackUrl(String path) {
    String base = publicWebUrl == null ? "" : publicWebUrl.trim();
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (base.isBlank()) base = "http://localhost:5173";
    return base + path;
  }

  private static Instant truncateMinute(Instant time) {
    if (time == null) return null;
    return time.atZone(ZONE).withSecond(0).withNano(0).toInstant();
  }

  private static String formatInstant(Instant time) {
    if (time == null) return "TBD";
    return DATE_FMT.format(time.atZone(ZONE)) + " at " + TIME_FMT.format(time);
  }

  private record SlotEstimate(int patientsOnSlot, String expectedDisplay) {}

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
