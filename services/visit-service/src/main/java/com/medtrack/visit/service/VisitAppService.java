package com.medtrack.visit.service;

import com.medtrack.common.dto.NotifyRequest;
import com.medtrack.common.port.VisitChartPort;
import com.medtrack.notify.service.NotificationAppService;
import com.medtrack.schedule.service.ScheduleAppService;
import com.medtrack.visit.domain.VisitEntity;
import com.medtrack.visit.domain.VisitEventEntity;
import com.medtrack.visit.repo.VisitEventRepository;
import com.medtrack.visit.repo.VisitRepository;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VisitAppService implements VisitChartPort {
  private final VisitRepository visitRepo;
  private final VisitEventRepository eventRepo;
  private final ScheduleAppService scheduleAppService;
  private final NotificationAppService notificationAppService;
  private final DoctorQueueService doctorQueueService;

  @Value("${medtrack.clinic-id}") private String clinicId;
  @Value("${medtrack.clinic-name}") private String clinicName;
  @Value("${medtrack.doctor-id}") private String seedDoctorId;
  @Value("${medtrack.doctor-name}") private String seedDoctorName;
  @Value("${medtrack.patient-name}") private String seedPatientName;

  public VisitAppService(
      VisitRepository visitRepo,
      VisitEventRepository eventRepo,
      @Lazy ScheduleAppService scheduleAppService,
      NotificationAppService notificationAppService,
      @Lazy DoctorQueueService doctorQueueService) {
    this.visitRepo = visitRepo;
    this.eventRepo = eventRepo;
    this.scheduleAppService = scheduleAppService;
    this.notificationAppService = notificationAppService;
    this.doctorQueueService = doctorQueueService;
  }

  @Override
  public List<VisitChartItem> listForDoctorDate(String doctorId, String date) {
    return list(doctorId, date).stream()
        .map(v -> new VisitChartItem(
            v.getId(),
            v.getPatientName(),
            v.getStatus(),
            v.getScheduledStart(),
            v.getScheduledEnd()))
        .toList();
  }

  public List<VisitEntity> list(String doctorId, String date) {
    if (doctorId != null && date != null) {
      ZoneId zone = ZoneId.of("Asia/Kolkata");
      LocalDate day = LocalDate.parse(date);
      Instant from = day.atStartOfDay(zone).toInstant();
      Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
      return visitRepo.findByDoctorIdAndScheduledStartBetweenOrderByScheduledStartAsc(
          doctorId, from, to);
    }
    return visitRepo.findAll();
  }

  /**
   * Check-in board. When hospitalId + doctorId are provided, returns only patients
   * booked for that doctor under that hospital (clinicId).
   */
  public List<VisitEntity> checkInBoard(String hospitalId, String doctorId) {
    List<String> statuses = List.of("BOOKED", "CALLED", "CHECKED_IN", "IN_CONSULT");
    String clinic = blankToNull(hospitalId);
    String doctor = blankToNull(doctorId);

    if (clinic != null && doctor != null) {
      return visitRepo.findByClinicIdAndDoctorIdAndStatusInOrderByScheduledStartAsc(
          clinic, doctor, statuses);
    }
    if (doctor != null) {
      return visitRepo.findByDoctorIdAndStatusInOrderByScheduledStartAsc(doctor, statuses);
    }
    return visitRepo.findByStatusInOrderByCheckedInAtAsc(statuses);
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  public VisitEntity get(String id) {
    return visitRepo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found"));
  }

  public Map<String, Object> detail(String id) {
    VisitEntity v = get(id);
    return Map.of(
        "visit", v,
        "events", eventRepo.findByVisitIdOrderByCreatedAtAsc(id));
  }

  @Transactional
  public VisitEntity create(Map<String, Object> body) {
    VisitEntity v = new VisitEntity();
    v.setClinicId(str(body, "clinicId", clinicId));
    if (body.get("hospitalId") != null) {
      try {
        v.setHospitalId(Long.parseLong(String.valueOf(body.get("hospitalId")).trim()));
      } catch (Exception ex) {
        v.setHospitalId(10001L);
      }
    } else {
      try {
        v.setHospitalId(Long.parseLong(v.getClinicId().trim()));
      } catch (Exception ex) {
        v.setHospitalId(10001L);
      }
    }
    v.setPatientId(str(body, "patientId", null));
    v.setPatientName(str(body, "patientName", seedPatientName));
    v.setPatientPhone(str(body, "patientPhone", null));
    v.setPatientEmail(str(body, "patientEmail", null));
    v.setSmsConsent(bool(body, "smsConsent", true));
    v.setEmailConsent(bool(body, "emailConsent", true));
    v.setDoctorId(str(body, "doctorId", seedDoctorId));
    v.setDoctorName(str(body, "doctorName", seedDoctorName));
    v.setStatus(str(body, "status", "BOOKED"));
    v.setReason(str(body, "reason", null));
    if (body.get("tokenNumber") != null) {
      try {
        v.setTokenNumber(Integer.parseInt(String.valueOf(body.get("tokenNumber")).trim()));
      } catch (Exception ignored) {
        // leave null
      }
    }
    if (body.get("scheduledStart") != null) {
      Instant start = Instant.parse(String.valueOf(body.get("scheduledStart")));
      v.setScheduledStart(start);
      v.setScheduledEnd(start.plus(Duration.ofMinutes(15)));
    }
    visitRepo.save(v);
    addEvent(v.getId(), v.getHospitalId(), "STATUS_CHANGE", null, v.getStatus(), "Visit created");
    boolean skipNotify = bool(body, "skipBookingNotify", false);
    if ("BOOKED".equals(v.getStatus()) && !skipNotify) {
      notify(v, "BOOKING_CONFIRMED");
    }
    return v;
  }

  @Transactional
  public VisitEntity checkIn(String id) {
    VisitEntity v = get(id);
    if (!List.of("BOOKED", "CALLED").contains(v.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot check in from " + v.getStatus());
    }
    String from = v.getStatus();
    ZoneId zone = ZoneId.of("Asia/Kolkata");
    LocalDate day = LocalDate.now(zone);
    Instant fromDay = day.atStartOfDay(zone).toInstant();
    Instant toDay = day.plusDays(1).atStartOfDay(zone).toInstant();
    int nextToken = (int) visitRepo
        .findByDoctorIdAndStatusInOrderByTokenNumberAsc(
            v.getDoctorId(), List.of("CHECKED_IN", "IN_CONSULT", "COMPLETED"))
        .stream()
        .filter(x -> x.getCheckedInAt() != null
            && !x.getCheckedInAt().isBefore(fromDay)
            && x.getCheckedInAt().isBefore(toDay))
        .count() + 1;

    v.setStatus("CHECKED_IN");
    v.setCheckedInAt(Instant.now());
    v.setTokenNumber(nextToken);
    visitRepo.save(v);
    addEvent(v.getId(), "STATUS_CHANGE", from, "CHECKED_IN", "Checked in, token=" + nextToken);
    addEvent(v.getId(), "TOKEN_ISSUED", null, null, "Token " + nextToken);
    notify(v, "CHECKED_IN");
    maybeYouAreNext(v.getDoctorId());
    try {
      doctorQueueService.syncOnCheckIn(v, DoctorQueueService.resolveHospitalId(v.getClinicId()));
    } catch (Exception ex) {
      addEvent(v.getId(), "QUEUE_SYNC", null, null, "Queue sync failed: " + ex.getMessage());
    }
    return v;
  }

  @Transactional
  public VisitEntity startConsult(String id) {
    VisitEntity v = get(id);
    String from = v.getStatus();
    if (!"CHECKED_IN".equals(from) && !"BOOKED".equals(from)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot start from " + from);
    }
    v.setStatus("IN_CONSULT");
    v.setActualStart(Instant.now());
    visitRepo.save(v);
    addEvent(v.getId(), "STATUS_CHANGE", from, "IN_CONSULT", "Consult started");
    notify(v, "CHECKUP_STARTED");
    try {
      doctorQueueService.syncOnStart(v, DoctorQueueService.resolveHospitalId(v.getClinicId()));
    } catch (Exception ex) {
      addEvent(v.getId(), "QUEUE_SYNC", null, null, "Queue sync failed: " + ex.getMessage());
    }
    return v;
  }

  @Transactional
  public VisitEntity markLate(String id, int minutes) {
    VisitEntity v = get(id);
    v.setDelayMinutes(v.getDelayMinutes() + minutes);
    visitRepo.save(v);
    addEvent(v.getId(), "DELAY", null, null, "Doctor late +" + minutes + " min");
    notify(v, "DOCTOR_DELAYED");
    return v;
  }

  @Transactional
  public VisitEntity complete(String id) {
    VisitEntity v = get(id);
    String from = v.getStatus();
    if (!"IN_CONSULT".equals(from) && !"CHECKED_IN".equals(from)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot complete from " + from);
    }
    Instant end = Instant.now();
    v.setActualEnd(end);
    if (v.getActualStart() == null) v.setActualStart(end.minus(Duration.ofMinutes(15)));
    int actualMinutes = (int) Duration.between(v.getActualStart(), end).toMinutes();
    if (actualMinutes < 1) actualMinutes = 1;

    Map<String, Object> fee;
    try {
      fee = scheduleAppService.calculateOvertime(v.getDoctorId(), actualMinutes);
    } catch (Exception ignored) {
      int fixed = 15;
      int over = Math.max(0, actualMinutes - fixed);
      int blocks = over == 0 ? 0 : (int) Math.ceil(over / 15.0);
      double ot = blocks * 200.0;
      fee = Map.of(
          "baseFee", 500.0,
          "overtimeMinutes", over,
          "overtimeFee", ot,
          "totalFee", 500.0 + ot,
          "currency", "INR");
    }

    v.setStatus("COMPLETED");
    v.setBaseFee(((Number) fee.get("baseFee")).doubleValue());
    v.setOvertimeMinutes(((Number) fee.get("overtimeMinutes")).intValue());
    v.setOvertimeFee(((Number) fee.get("overtimeFee")).doubleValue());
    v.setTotalFee(((Number) fee.get("totalFee")).doubleValue());
    v.setFeeCurrency(String.valueOf(fee.get("currency")));
    visitRepo.save(v);
    addEvent(v.getId(), "STATUS_CHANGE", from, "COMPLETED",
        "Completed. overtime=" + v.getOvertimeMinutes() + "m fee=" + v.getTotalFee());
    notify(v, "VISIT_COMPLETED");
    if (v.getOvertimeFee() > 0) {
      notify(v, "OVERTIME_FEE");
    }
    maybeYouAreNext(v.getDoctorId());
    try {
      doctorQueueService.syncOnComplete(v, DoctorQueueService.resolveHospitalId(v.getClinicId()));
    } catch (Exception ex) {
      addEvent(v.getId(), "QUEUE_SYNC", null, null, "Queue sync failed: " + ex.getMessage());
    }
    return v;
  }

  private void maybeYouAreNext(String doctorId) {
    List<VisitEntity> queue = visitRepo.findByDoctorIdAndStatusInOrderByTokenNumberAsc(
        doctorId, List.of("CHECKED_IN"));
    for (int i = 0; i < Math.min(2, queue.size()); i++) {
      notify(queue.get(i), "YOU_ARE_NEXT");
    }
  }

  private void addEvent(String visitId, String type, String from, String to, String message) {
    addEvent(visitId, null, type, from, to, message);
  }

  private void addEvent(
      String visitId, Long hospitalId, String type, String from, String to, String message) {
    Long hid = hospitalId;
    if (hid == null) {
      hid =
          visitRepo
              .findById(visitId)
              .map(VisitEntity::getHospitalId)
              .orElse(10001L);
    }
    VisitEventEntity e = new VisitEventEntity();
    e.setHospitalId(hid);
    e.setVisitId(visitId);
    e.setEventType(type);
    e.setFromStatus(from);
    e.setToStatus(to);
    e.setMessage(message);
    eventRepo.save(e);
  }

  private void notify(VisitEntity v, String eventCode) {
    try {
      NotifyRequest req = new NotifyRequest(
          v.getClinicId(),
          v.getId(),
          v.getPatientId(),
          eventCode,
          v.getPatientName(),
          v.getPatientPhone(),
          v.getPatientEmail(),
          v.isSmsConsent(),
          v.isEmailConsent(),
          v.getDoctorName(),
          clinicName,
          v.getScheduledStart() == null ? "TBD" : v.getScheduledStart().toString(),
          v.getTokenNumber() == null ? "—" : String.valueOf(v.getTokenNumber()),
          v.getDelayMinutes(),
          v.getStatus(),
          v.getOvertimeFee(),
          v.getTotalFee(),
          v.getFeeCurrency(),
          Map.of());
      notificationAppService.send(req);
      addEvent(v.getId(), "NOTIFY", null, null, eventCode + " queued (SMS+Email)");
    } catch (Exception ex) {
      addEvent(v.getId(), "NOTIFY", null, null, eventCode + " failed: " + ex.getMessage());
    }
  }

  private static String str(Map<String, Object> body, String key, String def) {
    Object v = body.get(key);
    return v == null ? def : String.valueOf(v);
  }

  private static boolean bool(Map<String, Object> body, String key, boolean def) {
    Object v = body.get(key);
    if (v == null) return def;
    return Boolean.parseBoolean(String.valueOf(v));
  }
}
