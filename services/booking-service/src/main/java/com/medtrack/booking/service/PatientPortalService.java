package com.medtrack.booking.service;

import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.domain.BookingEntity;
import com.medtrack.booking.domain.PatientEntity;
import com.medtrack.booking.domain.PatientReportEntity;
import com.medtrack.booking.domain.UserDetailsEntity;
import com.medtrack.booking.repo.AppointmentRepository;
import com.medtrack.booking.repo.PatientReportRepository;
import com.medtrack.booking.repo.PatientRepository;
import com.medtrack.booking.repo.UserDetailsRepository;
import com.medtrack.visit.service.DoctorQueueService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PatientPortalService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

  private final PatientRepository patientRepo;
  private final UserDetailsRepository userDetailsRepo;
  private final PatientReportRepository reportRepo;
  private final AppointmentRepository appointmentRepo;
  private final BookingAppService bookingService;
  private final DoctorQueueService queueService;
  private final AppointmentEnrichmentService enrichment;

  public PatientPortalService(
      PatientRepository patientRepo,
      UserDetailsRepository userDetailsRepo,
      PatientReportRepository reportRepo,
      AppointmentRepository appointmentRepo,
      BookingAppService bookingService,
      DoctorQueueService queueService,
      AppointmentEnrichmentService enrichment) {
    this.patientRepo = patientRepo;
    this.userDetailsRepo = userDetailsRepo;
    this.reportRepo = reportRepo;
    this.appointmentRepo = appointmentRepo;
    this.bookingService = bookingService;
    this.queueService = queueService;
    this.enrichment = enrichment;
  }

  public PatientEntity requirePatient(String phone) {
    String normalized = phone.replaceAll("\\D", "");
    return patientRepo
        .findByPhone(normalized)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
  }

  public Map<String, Object> bookings(String phone, String patientId) {
    String normalizedPhone = resolvePortalPhone(phone, patientId);
    if (normalizedPhone.isBlank() && (patientId == null || patientId.isBlank())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phone or patientId is required");
    }
    // USR… accounts live in user_details and may not have a legacy patients row
    requirePortalIdentity(normalizedPhone, patientId);
    List<Map<String, Object>> upcoming =
        bookingService.findUpcoming(normalizedPhone, patientId).stream()
            .map(this::toBookingSummary)
            .toList();
    List<Map<String, Object>> history =
        normalizedPhone.isBlank()
            ? List.of()
            : bookingService.findHistoryByPhone(normalizedPhone).stream()
                .map(this::toBookingSummary)
                .toList();
    return Map.of(
        "patientId", resolvePatientIdForPortal(normalizedPhone, patientId),
        "upcoming", upcoming,
        "history", history,
        "upcomingCount", upcoming.size(),
        "historyCount", history.size());
  }

  /**
   * Accept legacy patients.phone or user_details (USR… / phone) so portal status works for
   * User ID registrants who never got a patients-table row.
   */
  private void requirePortalIdentity(String normalizedPhone, String patientId) {
    if (patientId != null && !patientId.isBlank()) {
      String id = patientId.trim();
      if (id.toUpperCase().startsWith("USR")) {
        userDetailsRepo
            .findByUserId(id.toUpperCase())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        return;
      }
      if (patientRepo.findById(id).isPresent()) {
        return;
      }
    }
    if (normalizedPhone != null && !normalizedPhone.isBlank()) {
      if (userDetailsRepo.findByPhone(normalizedPhone).isPresent()
          || patientRepo.findByPhone(normalizedPhone).isPresent()) {
        return;
      }
      // Public /track?phone=… — hospital-booked walk-ins have appointments.phone_number only
      if (!appointmentRepo.findByPhoneNumberOrderByCreatedDateDesc(normalizedPhone).isEmpty()) {
        return;
      }
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found");
  }

  private String resolvePortalPhone(String phone, String patientId) {
    if (phone != null && !phone.replaceAll("\\D", "").isBlank()) {
      return phone.replaceAll("\\D", "");
    }
    if (patientId != null && !patientId.isBlank()) {
      String pid = patientId.trim();
      if (pid.toUpperCase().startsWith("USR")) {
        return userDetailsRepo
            .findByUserId(pid.toUpperCase())
            .map(u -> u.getPhone())
            .map(p -> p.replaceAll("\\D", ""))
            .orElse("");
      }
      return patientRepo
          .findById(pid)
          .map(PatientEntity::getPhone)
          .map(p -> p.replaceAll("\\D", ""))
          .orElse("");
    }
    return "";
  }

  public Map<String, Object> dashboard(String phone) {
    PatientEntity patient = requirePatient(phone);
    List<BookingEntity> upcoming = bookingService.findUpcoming(phone, null);
    BookingEntity next = upcoming.isEmpty() ? null : upcoming.get(0);
    Optional<BookingEntity> today = bookingService.findTodayActiveByPhone(phone);

    Map<String, Object> out = new HashMap<>();
    out.put(
        "patient",
        Map.of(
            "id", patient.getId(),
            "name", patient.getName(),
            "phone", patient.getPhone(),
            "age", patient.getAge() != null ? patient.getAge() : "",
            "gender", nullTo(patient.getGender(), ""),
            "email", nullTo(patient.getEmail(), "")));

    if (next != null) {
      out.put("nextAppointment", toBookingSummary(next));
    } else {
      out.put("nextAppointment", null);
    }

    if (today.isPresent()) {
      BookingEntity b = today.get();
      Long hospitalId = b.getHospitalId() != null ? b.getHospitalId() : 10001L;
      Map<String, Object> queue =
          queueService.status(hospitalId, b.getDoctorId(), b.getTokenNumber());
      out.put("queueSummary", summarizeQueue(queue, b));
    } else {
      out.put("queueSummary", null);
    }

    out.put("upcomingCount", upcoming.size());
    out.put("reportCount", reportRepo.findByPatientPhoneOrderByReportDateDescCreatedAtDesc(patient.getPhone()).size());
    return out;
  }

  public Map<String, Object> profile(String phone) {
    String normalized = phone == null ? "" : phone.replaceAll("\\D", "");
    Optional<PatientEntity> patientOpt = patientRepo.findByPhone(normalized);
    Optional<UserDetailsEntity> userOpt = userDetailsRepo.findByPhone(normalized);
    if (patientOpt.isEmpty() && userOpt.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found");
    }

    PatientEntity p = patientOpt.orElse(null);
    UserDetailsEntity u = userOpt.orElse(null);
    String email =
        firstNonBlank(
            p != null ? p.getEmail() : null, u != null ? u.getEmail() : null);
    String name =
        firstNonBlank(
            p != null ? p.getName() : null, u != null ? u.getUserName() : null);
    String id =
        p != null
            ? p.getId()
            : (u != null ? u.getUserId() : normalized);

    List<BookingEntity> history =
        normalized.isBlank() ? List.of() : bookingService.findHistoryByPhone(normalized);
    Map<String, Object> out = new HashMap<>();
    out.put(
        "profile",
        Map.of(
            "id", id,
            "name", nullTo(name, ""),
            "phone", normalized,
            "age", p != null && p.getAge() != null ? p.getAge() : "",
            "gender", p != null ? nullTo(p.getGender(), "") : "",
            "email", nullTo(email, ""),
            "address", p != null ? nullTo(p.getAddress(), "") : "",
            "bloodGroup", p != null ? nullTo(p.getBloodGroup(), "") : "",
            "emergencyContact", p != null ? nullTo(p.getEmergencyContact(), "") : ""));
    out.put("appointmentHistory", history.stream().map(this::toBookingSummary).toList());
    out.put(
        "reports",
        reportRepo.findByPatientPhoneOrderByReportDateDescCreatedAtDesc(normalized).stream()
            .map(this::toReportSummary)
            .toList());
    return out;
  }

  public Map<String, Object> updateProfile(String phone, Map<String, Object> body) {
    String normalized = phone == null ? "" : phone.replaceAll("\\D", "");
    Optional<PatientEntity> patientOpt = patientRepo.findByPhone(normalized);
    Optional<UserDetailsEntity> userOpt = userDetailsRepo.findByPhone(normalized);
    if (patientOpt.isEmpty() && userOpt.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found");
    }

    String email = text(body, "email");
    if (email != null) {
      email = email.trim().toLowerCase();
    }

    if (patientOpt.isPresent()) {
      PatientEntity p = patientOpt.get();
      String name = text(body, "name");
      if (name != null && !name.isBlank()) p.setName(name.trim());
      if (body.containsKey("age")) p.setAge(intOrNull(body.get("age")));
      if (body.containsKey("gender")) p.setGender(text(body, "gender"));
      if (body.containsKey("email")) p.setEmail(email);
      if (body.containsKey("address")) p.setAddress(text(body, "address"));
      if (body.containsKey("bloodGroup")) p.setBloodGroup(text(body, "bloodGroup"));
      if (body.containsKey("emergencyContact")) p.setEmergencyContact(text(body, "emergencyContact"));
      patientRepo.save(p);
    }

    if (userOpt.isPresent() && body.containsKey("email")) {
      UserDetailsEntity u = userOpt.get();
      u.setEmail(email == null || email.isBlank() ? null : email);
      String name = text(body, "name");
      if (name != null && !name.isBlank() && (u.getUserName() == null || u.getUserName().isBlank())) {
        u.setUserName(name.trim());
      }
      userDetailsRepo.save(u);
    }

    return Map.of("message", "Profile updated", "profile", profile(phone).get("profile"));
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v.trim();
      }
    }
    return null;
  }

  public Map<String, Object> queueStatus(String phone, String patientId) {
    String normalizedPhone = resolvePortalPhone(phone, patientId);
    requirePortalIdentity(normalizedPhone, patientId);

    BookingEntity b =
        bookingService
            .findTodayActive(normalizedPhone, patientId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No active appointment today for queue tracking"));
    Long hospitalId = b.getHospitalId() != null ? b.getHospitalId() : 10001L;
    String doctorId = b.getDoctorId();
    Integer yourToken = b.getTokenNumber();

    Map<String, Object> queue = queueService.status(hospitalId, doctorId, yourToken);
    // Overlay appointment-table totals for this doctor/hospital/date (BOOKED tokens included)
    enrichQueueWithAppointmentDayStats(queue, hospitalId, doctorId, yourToken);

    Map<String, Object> out = new HashMap<>(queue);
    Map<String, Object> bookingSummary = toBookingSummary(b);
    bookingSummary.put(
        "patientId",
        bookingSummary.getOrDefault(
            "patientId", resolvePatientIdForPortal(normalizedPhone, patientId)));
    // Prefer resolved doctor/hospital names from booking masters when queue board is empty
    Object bookingDoctorName = bookingSummary.get("doctorName");
    if (bookingDoctorName != null && !String.valueOf(bookingDoctorName).isBlank()) {
      out.put("doctorName", bookingDoctorName);
    }
    Object bookingDept = bookingSummary.get("department");
    if (bookingDept != null && !String.valueOf(bookingDept).isBlank()) {
      out.put("department", bookingDept);
    }
    out.put("booking", bookingSummary);
    out.put("patientId", resolvePatientIdForPortal(normalizedPhone, patientId));
    out.put("hospitalId", hospitalId);
    out.put("doctorId", doctorId);
    out.put("liveQueueStatus", computeLiveQueueStatus(out, yourToken));
    Map<String, Object> summary = buildQueueSummary(out, b);
    if (bookingDoctorName != null) {
      summary.put("doctorName", bookingDoctorName);
    }
    out.put("queueSummary", summary);
    return out;
  }

  /**
   * Uses appointments for hospital + doctor + same date to fill total token count, upcoming
   * tokens after the logged-in patient, and your token — even when doctor_queue is empty.
   */
  @SuppressWarnings("unchecked")
  private void enrichQueueWithAppointmentDayStats(
      Map<String, Object> queue, Long hospitalId, String doctorId, Integer yourToken) {
    LocalDate day = LocalDate.now(ZONE);
    List<AppointmentEntity> dayAppts =
        appointmentRepo
            .findByDoctorIdAndAppointmentDateOrderByAppointmentTimeAsc(doctorId, day)
            .stream()
            .filter(a -> hospitalId == null || hospitalId.equals(a.getHospitalId()))
            .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getStatus()))
            .toList();

    int totalTokens = dayAppts.size();
    if (totalTokens == 0) {
      return;
    }

    Map<String, Object> progress =
        queue.get("progress") instanceof Map<?, ?>
            ? new HashMap<>((Map<String, Object>) queue.get("progress"))
            : new HashMap<>();
    int queueTotal =
        progress.get("total") instanceof Number n ? n.intValue() : 0;
    // Prefer the larger of live queue rows vs appointment tokens for the day
    progress.put("total", Math.max(queueTotal, totalTokens));
    queue.put("progress", progress);
    queue.put("totalTokens", Math.max(queueTotal, totalTokens));
    queue.put("appointmentCountToday", totalTokens);

    List<Map<String, Object>> upcomingPatients = new ArrayList<>();
    for (AppointmentEntity a : dayAppts) {
      if (yourToken != null
          && a.getTokenNumber() != null
          && a.getTokenNumber() > yourToken) {
        Map<String, Object> row = new HashMap<>();
        row.put("token", a.getTokenNumber());
        row.put("patient", a.getPatientName() != null ? a.getPatientName() : "Patient");
        row.put("status", a.getStatus());
        row.put("patientId", a.getPatientId());
        upcomingPatients.add(row);
      }
    }
    queue.put("upcomingPatients", upcomingPatients);
    queue.put("upcomingPatientCount", upcomingPatients.size());

    if (yourToken != null) {
      queue.put("yourToken", yourToken);
      Map<String, Object> yourStatus =
          queue.get("yourStatus") instanceof Map<?, ?>
              ? new HashMap<>((Map<String, Object>) queue.get("yourStatus"))
              : new HashMap<>();
      yourStatus.putIfAbsent("token", yourToken);
      // Patients with lower token numbers still waiting/booked ahead of you
      long before =
          dayAppts.stream()
              .filter(a -> a.getTokenNumber() != null && a.getTokenNumber() < yourToken)
              .filter(a -> !"COMPLETED".equalsIgnoreCase(a.getStatus()))
              .count();
      if (yourStatus.get("patientsBefore") == null) {
        yourStatus.put("patientsBefore", (int) before);
      }
      queue.put("yourStatus", yourStatus);
    }

    if (queue.get("doctorName") == null || String.valueOf(queue.get("doctorName")).equals(doctorId)) {
      // keep enrichment service name if queue board had none
    }
  }

  private String resolvePatientIdForPortal(String phone, String patientId) {
    if (patientId != null && !patientId.isBlank()) {
      String id = patientId.trim();
      return id.toUpperCase().startsWith("USR") ? id.toUpperCase() : id;
    }
    String normalized = phone == null ? "" : phone.replaceAll("\\D", "");
    if (!normalized.isBlank()) {
      Optional<String> fromAppt =
          appointmentRepo.findByPhoneNumberOrderByCreatedDateDesc(normalized).stream()
              .map(AppointmentEntity::getPatientId)
              .filter(id -> id != null && !id.isBlank())
              .findFirst();
      if (fromAppt.isPresent()) {
        return fromAppt.get();
      }
      return patientRepo
          .findByPhone(normalized)
          .map(PatientEntity::getId)
          .orElse(normalized);
    }
    return "";
  }

  @SuppressWarnings("unchecked")
  private String computeLiveQueueStatus(Map<String, Object> queue, Integer tokenNumber) {
    if (tokenNumber == null) {
      return "BOOKED";
    }
    Map<String, Object> currentRunning = (Map<String, Object>) queue.get("currentRunning");
    if (currentRunning != null && currentRunning.get("token") instanceof Number running) {
      if (running.intValue() == tokenNumber) {
        return "IN_CONSULTATION";
      }
    }
    Map<String, Object> yourStatus = (Map<String, Object>) queue.get("yourStatus");
    if (yourStatus != null && yourStatus.get("patientsBefore") instanceof Number before) {
      return before.intValue() == 0 ? "YOU_ARE_NEXT" : "WAITING";
    }
    return "BOOKED";
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> buildQueueSummary(Map<String, Object> queue, BookingEntity b) {
    Map<String, Object> summary = new HashMap<>();
    summary.put("yourToken", b.getTokenNumber());
    summary.put("doctorName", queue.get("doctorName"));
    summary.put("department", queue.get("department"));
    summary.put("hospitalId", b.getHospitalId());
    summary.put("doctorId", b.getDoctorId());
    summary.put("waitingCount", queue.get("waitingCount"));
    summary.put("upcomingPatientCount", queue.get("upcomingPatientCount"));
    summary.put("totalTokens", queue.getOrDefault("totalTokens", null));
    Object progress = queue.get("progress");
    if (progress instanceof Map<?, ?> p) {
      Object total = p.get("total");
      summary.put("totalPatients", total);
      if (summary.get("totalTokens") == null) {
        summary.put("totalTokens", total);
      }
      summary.put("completedCount", p.get("completed"));
    } else {
      summary.put("totalPatients", queue.getOrDefault("appointmentCountToday", 0));
      if (summary.get("totalTokens") == null) {
        summary.put("totalTokens", summary.get("totalPatients"));
      }
      summary.put("completedCount", 0);
    }
    Map<String, Object> yourStatus = (Map<String, Object>) queue.get("yourStatus");
    if (yourStatus != null) {
      summary.put("patientsBefore", yourStatus.get("patientsBefore"));
      summary.put("waitMinutes", yourStatus.get("waitMinutes"));
      summary.put("expectedTurnTime", yourStatus.get("expectedTurnTime"));
    } else {
      summary.put("patientsBefore", null);
      summary.put("waitMinutes", null);
      summary.put("expectedTurnTime", null);
    }
    Map<String, Object> currentRunning = (Map<String, Object>) queue.get("currentRunning");
    if (currentRunning != null) {
      summary.put("currentRunningToken", currentRunning.get("token"));
      summary.put("currentRunningPatient", currentRunning.get("patient"));
      summary.put("currentRunningStatus", currentRunning.get("status"));
      summary.put(
          "currentConsultation",
          Map.of(
              "token", currentRunning.get("token"),
              "patient", currentRunning.get("patient"),
              "status", currentRunning.get("status")));
    } else {
      summary.put("currentRunningToken", null);
      summary.put("currentRunningPatient", null);
      summary.put("currentRunningStatus", null);
      summary.put("currentConsultation", null);
    }
    Object waitingBeforeYou = queue.get("waitingBeforeYou");
    if (waitingBeforeYou instanceof List<?> list) {
      summary.put("patientsAhead", list);
    } else {
      summary.put("patientsAhead", List.of());
    }
    return summary;
  }

  /** Dropdown source: distinct patient names from appointments for the same phone. */
  public Map<String, Object> appointmentPatients(String phone) {
    String normalized = phone == null ? "" : phone.replaceAll("\\D", "");
    if (normalized.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phone is required");
    }
    requirePortalIdentity(normalized, null);
    List<Map<String, Object>> patients = distinctPatientsFromAppointments(normalized);
    return Map.of("phone", normalized, "count", patients.size(), "patients", patients);
  }

  /**
   * Reports for a phone. When several appointment patients share the same phone, {@code
   * patients} lists distinct names from {@code appointments} for a dropdown; {@code
   * patientName} filters with WHERE patient_phone + patient_name.
   */
  public Map<String, Object> reports(
      String phone, String patientName, LocalDate from, LocalDate to) {
    String normalized = phone == null ? "" : phone.replaceAll("\\D", "");
    if (normalized.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phone is required");
    }
    requirePortalIdentity(normalized, null);

    List<Map<String, Object>> patients = distinctPatientsFromAppointments(normalized);
    String nameFilter = blankToNullRaw(patientName);

    List<PatientReportEntity> rows;
    if (nameFilter != null && from == null && to == null) {
      // Strict WHERE phone + patient_name
      rows = reportRepo.findByPhoneAndPatientName(normalized, nameFilter);
    } else {
      rows = reportRepo.search(normalized, nameFilter, from, to);
    }

    Map<String, Object> out = new HashMap<>();
    out.put("count", rows.size());
    out.put("reports", rows.stream().map(this::toReportSummary).toList());
    out.put("patients", patients);
    out.put("selectedPatientName", nameFilter == null ? "" : nameFilter);
    return out;
  }

  /** Distinct patient names for a phone from appointments (dropdown). */
  private List<Map<String, Object>> distinctPatientsFromAppointments(String normalizedPhone) {
    List<AppointmentEntity> appts =
        appointmentRepo.findByPhoneNumberOrderByCreatedDateDesc(normalizedPhone);
    Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
    for (AppointmentEntity a : appts) {
      String name = a.getPatientName() == null ? "" : a.getPatientName().trim();
      if (name.isBlank()) {
        continue;
      }
      String key = name.toLowerCase(Locale.ROOT);
      if (byName.containsKey(key)) {
        continue;
      }
      Map<String, Object> row = new HashMap<>();
      row.put("patientId", nullTo(a.getPatientId(), ""));
      row.put("patientName", name);
      row.put("age", a.getPatientAge() != null ? a.getPatientAge() : "");
      row.put("gender", nullTo(a.getGender(), ""));
      row.put("phone", normalizedPhone);
      byName.put(key, row);
    }
    return new ArrayList<>(byName.values());
  }

  public PatientReportEntity uploadReport(Map<String, Object> body) {
    String phone = text(body, "patientPhone");
    if (phone == null || phone.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientPhone is required");
    }
    String normalized = phone.replaceAll("\\D", "");
    String reportType = text(body, "reportType");
    String title = text(body, "title");
    if (reportType == null || reportType.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reportType is required");
    }
    if (title == null || title.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
    }

    PatientReportEntity r = new PatientReportEntity();
    r.setPatientPhone(normalized);
    r.setPatientName(text(body, "patientName"));
    r.setHospitalId(longOrNull(body.get("hospitalId")));
    r.setDoctorId(text(body, "doctorId"));
    r.setDoctorName(text(body, "doctorName"));
    r.setVisitId(text(body, "visitId"));
    r.setReportType(reportType.trim().toUpperCase());
    r.setTitle(title.trim());
    r.setDescription(text(body, "description"));
    r.setFileUrl(text(body, "fileUrl"));
    String reportDate = text(body, "reportDate");
    if (reportDate != null && !reportDate.isBlank()) {
      r.setReportDate(LocalDate.parse(reportDate));
    } else {
      r.setReportDate(LocalDate.now());
    }
    return reportRepo.save(r);
  }

  private Map<String, Object> toBookingSummary(BookingEntity b) {
    return enrichment.enrichBookingSummary(b);
  }

  private Map<String, Object> toReportSummary(PatientReportEntity r) {
    Map<String, Object> m = new HashMap<>();
    m.put("id", r.getId());
    m.put("patientName", nullTo(r.getPatientName(), ""));
    m.put("reportType", r.getReportType());
    m.put("title", r.getTitle());
    m.put("description", nullTo(r.getDescription(), ""));
    m.put("fileUrl", nullTo(r.getFileUrl(), ""));
    m.put("doctorName", nullTo(r.getDoctorName(), ""));
    m.put("reportDate", r.getReportDate() != null ? r.getReportDate().toString() : "");
    m.put("createdAt", r.getCreatedAt().toString());
    return m;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> summarizeQueue(Map<String, Object> queue, BookingEntity b) {
    Map<String, Object> summary = new HashMap<>();
    summary.put("doctorName", queue.get("doctorName"));
    summary.put("department", queue.get("department"));
    summary.put("tokenNumber", b.getTokenNumber());
    Object yours = queue.get("yourStatus");
    if (yours instanceof Map<?, ?> ys) {
      summary.put("patientsBefore", ys.get("patientsBefore"));
      summary.put("waitMinutes", ys.get("waitMinutes"));
      summary.put("expectedTurnTime", ys.get("expectedTurnTime"));
    }
    Object running = queue.get("currentRunning");
    if (running instanceof Map<?, ?> cr) {
      summary.put("currentRunningToken", cr.get("token"));
    } else {
      summary.put("currentRunningToken", null);
    }
    return summary;
  }

  private static String nullTo(String v, String d) {
    return v == null || v.isBlank() ? d : v;
  }

  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim().toUpperCase();
  }

  private static String blankToNullRaw(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }

  private static String text(Map<String, Object> body, String key) {
    Object v = body.get(key);
    return v == null ? null : String.valueOf(v);
  }

  private static Integer intOrNull(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(String.valueOf(v));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Long longOrNull(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.longValue();
    try {
      return Long.parseLong(String.valueOf(v));
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
