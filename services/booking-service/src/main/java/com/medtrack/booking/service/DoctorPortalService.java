package com.medtrack.booking.service;

import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.domain.DocumentEntity;
import com.medtrack.booking.repo.AppointmentRepository;
import com.medtrack.booking.repo.DocumentRepository;
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
import org.springframework.web.server.ResponseStatusException;

@Service
public class DoctorPortalService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

  private final AppointmentRepository appointmentRepo;
  private final DocumentRepository documentRepo;
  private final AppointmentEnrichmentService enrichment;
  private final ScheduleAppService scheduleService;

  public DoctorPortalService(
      AppointmentRepository appointmentRepo,
      DocumentRepository documentRepo,
      AppointmentEnrichmentService enrichment,
      ScheduleAppService scheduleService) {
    this.appointmentRepo = appointmentRepo;
    this.documentRepo = documentRepo;
    this.enrichment = enrichment;
    this.scheduleService = scheduleService;
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

    List<Map<String, Object>> patients = new ArrayList<>();
    for (AppointmentEntity a : rows) {
      Map<String, Object> row = enrichment.enrichAppointment(a);
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

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("date", day.toString());
    out.put("hospitalId", hospitalId);
    out.put("doctorId", doctorId.trim());
    out.put("doctorName", enrichment.doctorName(doctorId.trim()));
    out.put("hospitalName", enrichment.hospitalName(hospitalId));
    out.put("tokenCount", patients.size());
    out.put("withinClinicHours", withinClinicHours);
    out.put("scheduleWindows", todayWindows.stream().map(this::windowMap).toList());
    out.put("patients", patients);
    out.put("count", patients.size());
    return out;
  }

  /** Patient detail + same-hospital uploaded documents for popup. */
  @Transactional(readOnly = true)
  public Map<String, Object> patientDetail(
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

    Map<String, Object> patient = enrichment.enrichAppointment(a);
    patient.put("phoneNumber", a.getPhoneNumber());
    patient.put("patientPhone", a.getPhoneNumber());
    patient.put(
        "appointmentTimeLabel",
        a.getAppointmentTime() == null
            ? ""
            : TIME_FMT.format(a.getAppointmentTime().atZone(ZONE).toLocalTime()));

    List<Map<String, Object>> documents = new ArrayList<>();
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
        documents.addAll(flattenDocument(d));
      }
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("patient", patient);
    out.put("documents", documents);
    out.put("documentCount", documents.size());
    return out;
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
      row.put("documentType", type);
      row.put("documentName", name);
      row.put("documentPath", vals[i]);
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
