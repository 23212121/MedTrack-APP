package com.medtrack.booking.service;

import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.domain.BookingEntity;
import com.medtrack.booking.domain.CareChatEntity;
import com.medtrack.booking.domain.DocumentEntity;
import com.medtrack.booking.domain.DoctorProfessionalEntity;
import com.medtrack.booking.repo.AppointmentRepository;
import com.medtrack.booking.repo.BookingRepository;
import com.medtrack.booking.repo.CareChatRepository;
import com.medtrack.booking.repo.DocumentRepository;
import com.medtrack.booking.repo.DoctorProfessionalRepository;
import com.medtrack.schedule.domain.DoctorScheduleEntity;
import com.medtrack.schedule.service.ScheduleAppService;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DoctorPortalService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

  private final AppointmentRepository appointmentRepo;
  private final BookingRepository bookingRepo;
  private final DocumentRepository documentRepo;
  private final DoctorProfessionalRepository professionalRepo;
  private final CareChatRepository chatRepo;
  private final AppointmentEnrichmentService enrichment;
  private final ScheduleAppService scheduleService;
  private final DocumentUploadService documentUpload;

  public DoctorPortalService(
      AppointmentRepository appointmentRepo,
      BookingRepository bookingRepo,
      DocumentRepository documentRepo,
      DoctorProfessionalRepository professionalRepo,
      CareChatRepository chatRepo,
      AppointmentEnrichmentService enrichment,
      ScheduleAppService scheduleService,
      DocumentUploadService documentUpload) {
    this.appointmentRepo = appointmentRepo;
    this.bookingRepo = bookingRepo;
    this.documentRepo = documentRepo;
    this.professionalRepo = professionalRepo;
    this.chatRepo = chatRepo;
    this.enrichment = enrichment;
    this.scheduleService = scheduleService;
    this.documentUpload = documentUpload;
  }

  /**
   * Same-day patient tiles for a doctor at a hospital. Both hospitalId and doctorId must match
   * appointment rows; other doctors' patients are excluded.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> todayPatients(Long hospitalId, String doctorId, LocalDate date) {
    if (hospitalId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hospital id is required");
    }
    if (doctorId == null || doctorId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Doctor id is required");
    }
    LocalDate day = date != null ? date : LocalDate.now(ZONE);
    List<AppointmentEntity> rows =
        appointmentRepo
            .findByHospitalIdAndDoctorIdAndAppointmentDateAndStatusNotOrderByTokenNumberAscAppointmentTimeAsc(
                hospitalId, doctorId.trim(), day, "CANCELLED");

    List<DoctorScheduleEntity> weekly = scheduleService.weekly(doctorId.trim());
    int dow = scheduleDayOfWeek(day);
    List<DoctorScheduleEntity> todayWindows =
        weekly.stream().filter(s -> s.getDayOfWeek() == dow).toList();

    LocalTime now = LocalTime.now(ZONE);
    boolean withinClinicHours = isWithinAnyWindow(now, todayWindows);

    Map<String, List<CareChatEntity>> chats =
        rows.isEmpty()
            ? Map.of()
            : chatRepo.findByAppointmentIdIn(rows.stream().map(AppointmentEntity::getId).toList()).stream()
                .filter(row -> row.getAppointmentId() != null && !row.getAppointmentId().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(CareChatEntity::getAppointmentId));
    List<Map<String, Object>> patients = new ArrayList<>();
    for (AppointmentEntity a : rows) {
      Map<String, Object> row = enrichment.enrichAppointment(a);
      List<CareChatEntity> thread = chats.getOrDefault(a.getId(), List.of());
      List<String> otherAts = CareChatService.otherMessageAts(thread, "DOCTOR");
      row.put("chatCount", thread.size());
      row.put("unreadCount", otherAts.size());
      row.put("otherMessageAts", otherAts);
      row.put("phoneNumber", a.getPhoneNumber());
      row.put("patientPhone", a.getPhoneNumber());
      boolean inSchedule = isAppointmentInSchedule(a.getAppointmentTime(), todayWindows);
      row.put("withinSchedule", inSchedule);
      row.put("timingOk", inSchedule);
      row.put(
          "appointmentTimeLabel",
          a.getAppointmentTime() == null
              ? ""
              : TIME_FMT.format(a.getAppointmentTime().atZone(ZONE).toLocalTime()));
      patients.add(row);
    }
    patients.sort(
        Comparator.comparing(
            (Map<String, Object> m) -> {
              Object t = m.get("tokenNumber");
              if (t instanceof Number n) return n.intValue();
              return Integer.MAX_VALUE;
            }));

    int bookedCount = 0;
    int inProcessCount = 0;
    int completedCount = 0;
    for (Map<String, Object> row : patients) {
      String wf = workflowStatus(row.get("status") == null ? null : String.valueOf(row.get("status")));
      row.put("status", wf);
      row.put("workflowStatus", wf);
      switch (wf) {
        case "IN-PROCESS" -> inProcessCount++;
        case "COMPLETED" -> completedCount++;
        default -> bookedCount++;
      }
    }
    long cancelledCount =
        appointmentRepo.countByHospitalIdAndDoctorIdAndAppointmentDateAndStatus(
            hospitalId, doctorId.trim(), day, "CANCELLED");

    DoctorProfessionalEntity professional = professionalRepo.findById(doctorId.trim()).orElse(null);
    String specialization = professional == null ? null : professional.getSpecialization();
    String department = professional == null ? null : professional.getDepartment();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("date", day.toString());
    out.put("hospitalId", hospitalId);
    out.put("doctorId", doctorId.trim());
    out.put("doctorName", enrichment.doctorName(doctorId.trim()));
    out.put("specialization", specialization);
    out.put("department", department);
    out.put("hospitalName", enrichment.hospitalName(hospitalId));
    out.put("tokenCount", patients.size());
    out.put("totalBookings", patients.size());
    out.put("bookedCount", bookedCount);
    out.put("inProcessCount", inProcessCount);
    out.put("completedCount", completedCount);
    out.put("cancelledCount", cancelledCount);
    out.put("patientCount", patients.size() + cancelledCount);
    out.put("withinClinicHours", withinClinicHours);
    out.put("scheduleWindows", todayWindows.stream().map(this::windowMap).toList());
    out.put("patients", patients);
    out.put("count", patients.size());
    return out;
  }

  /** BOOKED → IN-PROCESS (select token) → COMPLETED. Scoped to this doctor + hospital. */
  @Transactional
  public Map<String, Object> updateStatus(
      Long hospitalId, String doctorId, String appointmentId, String actionOrStatus) {
    AppointmentEntity a = requireAssignedAppointment(hospitalId, doctorId, appointmentId);
    String current = workflowStatus(a.getStatus());
    String next = resolveNextStatus(actionOrStatus, current);
    a.setStatus(next);
    a.setUpdatedBy(doctorId);
    appointmentRepo.save(a);
    if (a.getBookingRefId() != null && !a.getBookingRefId().isBlank()) {
      bookingRepo
          .findById(a.getBookingRefId())
          .ifPresent(
              (BookingEntity b) -> {
                if (hospitalId.equals(b.getHospitalId())
                    && doctorId.equalsIgnoreCase(b.getDoctorId())) {
                  b.setStatus(next);
                  bookingRepo.save(b);
                }
              });
    }
    return patientDetail(hospitalId, doctorId, appointmentId);
  }

  /** Upload RX / patient files for an assigned booking. Uses existing svc.documents. */
  @Transactional
  public Map<String, Object> uploadPatientDocuments(
      Long hospitalId,
      String doctorId,
      String appointmentId,
      String aadhaarNumber,
      MultipartFile[] files,
      String uploadedBy) {
    AppointmentEntity a = requireAssignedAppointment(hospitalId, doctorId, appointmentId);
    String phone = a.getPhoneNumber() == null ? "" : a.getPhoneNumber();
    if (phone.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Patient phone is required to attach documents");
    }
    documentUpload.upload(
        hospitalId,
        a.getPatientName(),
        aadhaarNumber,
        phone,
        files,
        uploadedBy == null || uploadedBy.isBlank() ? doctorId : uploadedBy,
        "RX");
    return patientDetail(hospitalId, doctorId, appointmentId);
  }

  /** Patient detail + same-hospital uploaded documents for popup. */
  @Transactional(readOnly = true)
  public Map<String, Object> patientDetail(
      Long hospitalId, String doctorId, String appointmentId) {
    AppointmentEntity a = requireAssignedAppointment(hospitalId, doctorId, appointmentId);

    Map<String, Object> patient = enrichment.enrichAppointment(a);
    String wf = workflowStatus(a.getStatus());
    patient.put("status", wf);
    patient.put("workflowStatus", wf);
    patient.put("phoneNumber", a.getPhoneNumber());
    patient.put("patientPhone", a.getPhoneNumber());
    patient.put("currentComplaint", a.getReason());
    patient.put("allergies", null);
    patient.put("medicalHistory", null);
    patient.put(
        "appointmentTimeLabel",
        a.getAppointmentTime() == null
            ? ""
            : TIME_FMT.format(a.getAppointmentTime().atZone(ZONE).toLocalTime()));
    patient.put("appointmentDate", a.getAppointmentDate() == null ? null : a.getAppointmentDate().toString());
    patient.put("doctorName", enrichment.doctorName(doctorId.trim()));
    patient.put("hospitalName", enrichment.hospitalName(hospitalId));

    List<Map<String, Object>> documents = new ArrayList<>();
    String knownAadhaar = null;
    String phone = a.getPhoneNumber() == null ? "" : a.getPhoneNumber().replaceAll("\\D", "");
    if (!phone.isBlank()) {
      List<DocumentEntity> docs =
          documentRepo.findByHospitalIdAndPhoneNumberOrderByCreationDateDesc(hospitalId, phone);
      // Also try raw stored phone if different formatting
      if (docs.isEmpty() && a.getPhoneNumber() != null) {
        docs =
            documentRepo.findByHospitalIdAndPhoneNumberOrderByCreationDateDesc(
                hospitalId, a.getPhoneNumber().trim());
      }
      String patientName = a.getPatientName() == null ? "" : a.getPatientName().trim();
      for (DocumentEntity d : docs) {
        if (!patientName.isBlank()
            && d.getPatientName() != null
            && !d.getPatientName().trim().equalsIgnoreCase(patientName)) {
          continue;
        }
        if (knownAadhaar == null && d.getAadhaarNumber() != null && !d.getAadhaarNumber().isBlank()) {
          knownAadhaar = d.getAadhaarNumber();
        }
        documents.addAll(flattenDocument(d));
      }
    }
    patient.put("aadhaarNumber", knownAadhaar);

    List<Map<String, Object>> previous = previousRecords(hospitalId, a);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("patient", patient);
    out.put("documents", documents);
    out.put("documentCount", documents.size());
    out.put("previousRecords", previous);
    return out;
  }

  private AppointmentEntity requireAssignedAppointment(
      Long hospitalId, String doctorId, String appointmentId) {
    if (hospitalId == null || doctorId == null || doctorId.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Hospital id and doctor id are required");
    }
    AppointmentEntity a =
        appointmentRepo
            .findById(appointmentId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
    if (!hospitalId.equals(a.getHospitalId())
        || !doctorId.trim().equalsIgnoreCase(a.getDoctorId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Appointment belongs to another doctor/hospital");
    }
    if ("CANCELLED".equalsIgnoreCase(a.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment is cancelled");
    }
    return a;
  }

  private List<Map<String, Object>> previousRecords(Long hospitalId, AppointmentEntity current) {
    String phone = current.getPhoneNumber() == null ? "" : current.getPhoneNumber().replaceAll("\\D", "");
    if (phone.isBlank()) return List.of();
    return appointmentRepo.findByPhoneNumberOrderByCreatedDateDesc(phone).stream()
        .filter(row -> hospitalId.equals(row.getHospitalId()))
        .filter(row -> !row.getId().equals(current.getId()))
        .filter(row -> !"CANCELLED".equalsIgnoreCase(row.getStatus()))
        .limit(8)
        .map(
            row -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", row.getId());
              m.put("appointmentDate", row.getAppointmentDate() == null ? null : row.getAppointmentDate().toString());
              m.put("status", workflowStatus(row.getStatus()));
              m.put("tokenNumber", row.getTokenNumber());
              m.put("doctorName", enrichment.doctorName(row.getDoctorId()));
              m.put("reason", row.getReason());
              return m;
            })
        .toList();
  }

  static String workflowStatus(String raw) {
    if (raw == null || raw.isBlank()) return "BOOKED";
    String s = raw.trim().toUpperCase().replace('_', '-');
    if (s.equals("IN-PROCESS")
        || s.equals("IN-CONSULT")
        || s.equals("IN-CONSULTATION")
        || s.equals("RUNNING")) {
      return "IN-PROCESS";
    }
    if (s.equals("COMPLETED") || s.equals("COMPLETE")) return "COMPLETED";
    if (s.equals("CANCELLED") || s.equals("CANCELED")) return "CANCELLED";
    return "BOOKED".equals(s) ? "BOOKED" : s;
  }

  private static String resolveNextStatus(String actionOrStatus, String current) {
    String raw = actionOrStatus == null ? "" : actionOrStatus.trim().toUpperCase().replace('_', '-');
    if (raw.equals("START") || raw.equals("IN-PROCESS")) {
      if (!"BOOKED".equals(current)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Only a booked patient can be moved to In-Process");
      }
      return "IN-PROCESS";
    }
    if (raw.equals("COMPLETE") || raw.equals("COMPLETED")) {
      if (!"IN-PROCESS".equals(current)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Mark the patient In-Process before completing");
      }
      return "COMPLETED";
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Status must follow BOOKED → IN-PROCESS → COMPLETED");
  }

  private List<Map<String, Object>> flattenDocument(DocumentEntity d) {
    List<Map<String, Object>> rows = new ArrayList<>();
    String[] vals = {
      d.getFileUpload1(),
      d.getFileUpload2(),
      d.getFileUpload3(),
      d.getFileUpload4(),
      d.getFileUpload5()
    };
    String type =
        d.getDocumentType() == null || d.getDocumentType().isBlank()
            ? "TEST"
            : d.getDocumentType();
    for (int i = 0; i < vals.length; i++) {
      if (vals[i] == null || vals[i].isBlank()) continue;
      String name = vals[i].replace('\\', '/');
      int slash = name.lastIndexOf('/');
      if (slash >= 0) name = name.substring(slash + 1);
      name = name.replaceFirst("^\\d+_", "");
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", d.getId());
      row.put("slot", i + 1);
      row.put("patientName", d.getPatientName());
      row.put("aadhaarNumber", d.getAadhaarNumber());
      row.put("documentType", type);
      row.put("documentName", name);
      row.put("documentPath", vals[i]);
      row.put("downloadUrl", "/api/documents/" + d.getId() + "/files/" + (i + 1));
      row.put(
          "uploadDate",
          d.getCreationDate() != null ? d.getCreationDate().toString() : null);
      rows.add(row);
    }
    return rows;
  }

  private Map<String, Object> windowMap(DoctorScheduleEntity s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("startTime", s.getStartTime());
    m.put("endTime", s.getEndTime());
    m.put("slotMinutes", s.getSlotMinutes());
    return m;
  }

  private static int scheduleDayOfWeek(LocalDate day) {
    DayOfWeek dow = day.getDayOfWeek();
    return dow == DayOfWeek.SUNDAY ? 0 : dow.getValue() % 7;
  }

  private static boolean isAppointmentInSchedule(
      Instant appointmentTime, List<DoctorScheduleEntity> windows) {
    if (appointmentTime == null) return windows.isEmpty();
    if (windows.isEmpty()) {
      // No weekly schedule configured — allow same-day appointments
      return true;
    }
    LocalTime t = appointmentTime.atZone(ZONE).toLocalTime();
    return isWithinAnyWindow(t, windows);
  }

  private static boolean isWithinAnyWindow(LocalTime t, List<DoctorScheduleEntity> windows) {
    if (windows == null || windows.isEmpty()) return true;
    for (DoctorScheduleEntity w : windows) {
      LocalTime start = parseTime(w.getStartTime());
      LocalTime end = parseTime(w.getEndTime());
      if (start == null || end == null) continue;
      if (!t.isBefore(start) && !t.isAfter(end)) return true;
    }
    return false;
  }

  private static LocalTime parseTime(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String v = raw.trim();
    try {
      if (v.length() == 5) return LocalTime.parse(v, TIME_FMT);
      return LocalTime.parse(v);
    } catch (Exception ex) {
      return null;
    }
  }
}
