package com.medtrack.visit.service;

import com.medtrack.common.dto.NotifyRequest;
import com.medtrack.notify.service.NotificationAppService;
import com.medtrack.schedule.domain.DoctorAvailabilityEntity;
import com.medtrack.schedule.repo.DoctorAvailabilityRepository;
import com.medtrack.visit.domain.DoctorQueueEntity;
import com.medtrack.visit.domain.VisitEntity;
import com.medtrack.visit.repo.DoctorQueueRepository;
import com.medtrack.visit.repo.VisitRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DoctorQueueService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");
  private static final List<String> LIVE_STATUSES =
      List.of("CHECKED_IN", "IN_CONSULT", "COMPLETED");

  private final DoctorQueueRepository queueRepo;
  private final VisitRepository visitRepo;
  private final NotificationAppService notificationAppService;
  private final DoctorAvailabilityRepository availabilityRepo;

  @Value("${medtrack.avg-consultation-minutes:10}")
  private int avgConsultationMinutes;

  @Value("${medtrack.clinic-name:MedTrack Clinic}")
  private String defaultClinicName;

  public DoctorQueueService(
      DoctorQueueRepository queueRepo,
      VisitRepository visitRepo,
      NotificationAppService notificationAppService,
      DoctorAvailabilityRepository availabilityRepo) {
    this.queueRepo = queueRepo;
    this.visitRepo = visitRepo;
    this.notificationAppService = notificationAppService;
    this.availabilityRepo = availabilityRepo;
  }

  public LocalDate today() {
    return LocalDate.now(ZONE);
  }

  @Transactional
  public DoctorQueueEntity syncOnCheckIn(VisitEntity v, Long hospitalId) {
    if (v.getTokenNumber() == null || v.getDoctorId() == null) {
      return null;
    }
    Long hid = hospitalId != null ? hospitalId : resolveHospitalId(v.getClinicId());
    LocalDate day = today();
    DoctorQueueEntity row =
        queueRepo
            .findByVisitId(v.getId())
            .orElseGet(
                () ->
                    queueRepo
                        .findByHospitalIdAndDoctorIdAndQueueDateAndTokenNo(
                            hid, v.getDoctorId(), day, v.getTokenNumber())
                        .orElseGet(DoctorQueueEntity::new));

    row.setHospitalId(hid);
    row.setDoctorId(v.getDoctorId());
    row.setDoctorName(v.getDoctorName());
    row.setTokenNo(v.getTokenNumber());
    row.setPatientId(v.getPatientId());
    row.setPatientName(v.getPatientName());
    row.setPatientPhone(v.getPatientPhone());
    row.setVisitId(v.getId());
    row.setStatus("WAITING");
    row.setCheckinTime(v.getCheckedInAt() != null ? v.getCheckedInAt() : Instant.now());
    row.setQueueDate(day);
    return queueRepo.save(row);
  }

  @Transactional
  public DoctorQueueEntity syncOnStart(VisitEntity v, Long hospitalId) {
    Long hid = hospitalId != null ? hospitalId : resolveHospitalId(v.getClinicId());
    LocalDate day = today();

    // Complete any other RUNNING for this doctor today
    List<DoctorQueueEntity> running =
        queueRepo.findByHospitalIdAndDoctorIdAndQueueDateAndStatusOrderByTokenNoAsc(
            hid, v.getDoctorId(), day, "RUNNING");
    Instant now = Instant.now();
    for (DoctorQueueEntity r : running) {
      if (v.getId() != null && v.getId().equals(r.getVisitId())) {
        continue;
      }
      r.setStatus("COMPLETED");
      r.setConsultationEnd(now);
      queueRepo.save(r);
    }

    DoctorQueueEntity row =
        queueRepo
            .findByVisitId(v.getId())
            .orElseGet(
                () -> {
                  DoctorQueueEntity created = syncOnCheckIn(v, hid);
                  return created != null ? created : new DoctorQueueEntity();
                });

    row.setHospitalId(hid);
    row.setDoctorId(v.getDoctorId());
    row.setDoctorName(v.getDoctorName());
    if (v.getTokenNumber() != null) {
      row.setTokenNo(v.getTokenNumber());
    }
    row.setPatientId(v.getPatientId());
    row.setPatientName(v.getPatientName());
    row.setPatientPhone(v.getPatientPhone());
    row.setVisitId(v.getId());
    row.setStatus("RUNNING");
    row.setConsultationStart(v.getActualStart() != null ? v.getActualStart() : now);
    row.setQueueDate(day);
    if (row.getCheckinTime() == null) {
      row.setCheckinTime(now);
    }
    DoctorQueueEntity saved = queueRepo.save(row);
    notifyTwoAhead(hid, v.getDoctorId(), day, v.getDoctorName());
    return saved;
  }

  @Transactional
  public DoctorQueueEntity syncOnComplete(VisitEntity v, Long hospitalId) {
    Long hid = hospitalId != null ? hospitalId : resolveHospitalId(v.getClinicId());
    LocalDate day = today();
    Instant now = Instant.now();

    Optional<DoctorQueueEntity> existing = queueRepo.findByVisitId(v.getId());
    DoctorQueueEntity row =
        existing.orElseGet(
            () ->
                queueRepo
                    .findByHospitalIdAndDoctorIdAndQueueDateAndTokenNo(
                        hid,
                        v.getDoctorId(),
                        day,
                        v.getTokenNumber() == null ? -1 : v.getTokenNumber())
                    .orElse(null));
    if (row == null) {
      notifyTwoAhead(hid, v.getDoctorId(), day, v.getDoctorName());
      return null;
    }
    row.setStatus("COMPLETED");
    row.setConsultationEnd(v.getActualEnd() != null ? v.getActualEnd() : now);
    DoctorQueueEntity saved = queueRepo.save(row);
    notifyTwoAhead(hid, v.getDoctorId(), day, v.getDoctorName());
    return saved;
  }

  public Map<String, Object> board(Long hospitalId, String doctorId) {
    return status(hospitalId, doctorId, null);
  }

  /**
   * Refresh today's doctor_queue rows from svc.visits so patients always see
   * live DB data for the current Asia/Kolkata calendar day only.
   */
  @Transactional
  public void syncTodayFromVisits(Long hospitalId, String doctorId) {
    LocalDate day = today();
    Instant from = day.atStartOfDay(ZONE).toInstant();
    Instant to = day.plusDays(1).atStartOfDay(ZONE).toInstant();
    String doctor = doctorId.trim();
    String hospitalKey = String.valueOf(hospitalId);

    List<VisitEntity> visits =
        visitRepo.findTodayQueueForDoctor(doctor, LIVE_STATUSES, from, to);

    java.util.HashSet<String> keepVisitIds = new java.util.HashSet<>();

    for (VisitEntity v : visits) {
      if (!belongsToHospital(v, hospitalKey, hospitalId)) {
        continue;
      }
      if (v.getTokenNumber() == null) {
        continue;
      }
      String queueStatus = mapVisitStatus(v.getStatus());
      if (queueStatus == null) {
        continue;
      }

      DoctorQueueEntity row =
          queueRepo
              .findByVisitId(v.getId())
              .orElseGet(
                  () ->
                      queueRepo
                          .findByHospitalIdAndDoctorIdAndQueueDateAndTokenNo(
                              hospitalId, doctor, day, v.getTokenNumber())
                          .orElseGet(DoctorQueueEntity::new));

      row.setHospitalId(hospitalId);
      row.setDoctorId(doctor);
      row.setDoctorName(v.getDoctorName());
      row.setTokenNo(v.getTokenNumber());
      row.setPatientId(v.getPatientId());
      row.setPatientName(v.getPatientName());
      row.setPatientPhone(v.getPatientPhone());
      row.setVisitId(v.getId());
      row.setStatus(queueStatus);
      row.setQueueDate(day);
      if (v.getCheckedInAt() != null) {
        row.setCheckinTime(v.getCheckedInAt());
      }
      if (v.getActualStart() != null) {
        row.setConsultationStart(v.getActualStart());
      }
      if (v.getActualEnd() != null) {
        row.setConsultationEnd(v.getActualEnd());
      }
      DoctorQueueEntity saved = queueRepo.save(row);
      if (saved.getVisitId() != null) {
        keepVisitIds.add(saved.getVisitId());
      }
    }

    // Drop today's stale rows not present in visits (keeps board = DB today only)
    List<DoctorQueueEntity> existing =
        queueRepo.findByHospitalIdAndDoctorIdAndQueueDateOrderByTokenNoAsc(
            hospitalId, doctor, day);
    for (DoctorQueueEntity row : existing) {
      String vid = row.getVisitId();
      if (vid == null || vid.isBlank() || !keepVisitIds.contains(vid)) {
        queueRepo.delete(row);
      }
    }
  }

  private static boolean belongsToHospital(VisitEntity v, String hospitalKey, Long hospitalId) {
    String clinic = v.getClinicId();
    if (clinic == null || clinic.isBlank()) {
      return true; // doctor already scoped by UI hospital filter
    }
    if (clinic.equals(hospitalKey) || clinic.equals(String.valueOf(hospitalId))) {
      return true;
    }
    // Legacy seed clinic ids still show under the selected hospital for that doctor
    return clinic.startsWith("seed-") || !clinic.chars().allMatch(Character::isDigit);
  }

  private static String mapVisitStatus(String visitStatus) {
    if (visitStatus == null) return null;
    return switch (visitStatus) {
      case "CHECKED_IN", "CALLED" -> "WAITING";
      case "IN_CONSULT" -> "RUNNING";
      case "COMPLETED" -> "COMPLETED";
      default -> null;
    };
  }

  public Map<String, Object> status(Long hospitalId, String doctorId, Integer tokenNo) {
    if (hospitalId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hospitalId is required");
    }
    if (doctorId == null || doctorId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "doctorId is required");
    }
    LocalDate day = today();
    // Always hydrate from visits DB for today before returning board
    syncTodayFromVisits(hospitalId, doctorId.trim());

    List<DoctorQueueEntity> all =
        queueRepo.findByHospitalIdAndDoctorIdAndQueueDateOrderByTokenNoAsc(
            hospitalId, doctorId.trim(), day);

    DoctorQueueEntity current =
        all.stream().filter(q -> "RUNNING".equals(q.getStatus())).findFirst().orElse(null);
    List<DoctorQueueEntity> waiting =
        all.stream().filter(q -> "WAITING".equals(q.getStatus())).toList();
    long completed =
        all.stream().filter(q -> "COMPLETED".equals(q.getStatus())).count();
    int total = all.size();
    int pct = total == 0 ? 0 : (int) Math.round((completed * 100.0) / total);

    String doctorName =
        all.stream()
            .map(DoctorQueueEntity::getDoctorName)
            .filter(n -> n != null && !n.isBlank())
            .findFirst()
            .orElse(doctorId);
    String department =
        all.stream()
            .map(DoctorQueueEntity::getDepartment)
            .filter(n -> n != null && !n.isBlank())
            .findFirst()
            .orElse("General Medicine");

    int currentToken = current != null && current.getTokenNo() != null ? current.getTokenNo() : 0;
    LocalTime nowLocal = LocalTime.now(ZONE);

    Map<String, Object> out = new HashMap<>();
    out.put("hospitalId", hospitalId);
    out.put("doctorId", doctorId.trim());
    out.put("doctorName", doctorName);
    out.put("department", department);
    out.put("queueDate", day.toString());
    out.put("currentTime", nowLocal.format(TIME_FMT));
    out.put("avgConsultationMinutes", avgConsultationMinutes);
    out.put("doctorAvailable", isDoctorAvailable(doctorId.trim()));
    out.put("waitingCount", waiting.size());

    if (current != null) {
      out.put(
          "currentRunning",
          Map.of(
              "token", current.getTokenNo(),
              "patient", nullTo(current.getPatientName(), "Patient"),
              "status", "In Consultation",
              "visitId", nullTo(current.getVisitId(), "")));
    } else {
      out.put("currentRunning", null);
    }

    List<Map<String, Object>> waitingList = new ArrayList<>();
    for (DoctorQueueEntity w : waiting) {
      waitingList.add(
          Map.of(
              "token", w.getTokenNo(),
              "patient", nullTo(w.getPatientName(), "Patient"),
              "status", "Waiting"));
    }
    out.put("waitingPatients", waitingList);

    List<Integer> upcomingTokens = new ArrayList<>();
    if (current != null) {
      upcomingTokens.add(current.getTokenNo());
    }
    for (DoctorQueueEntity w : waiting) {
      if (upcomingTokens.size() >= 4) break;
      upcomingTokens.add(w.getTokenNo());
    }
    Map<String, Object> upcoming = new HashMap<>();
    upcoming.put("current", upcomingTokens.size() > 0 ? upcomingTokens.get(0) : null);
    upcoming.put("next", upcomingTokens.size() > 1 ? upcomingTokens.get(1) : null);
    upcoming.put("then", upcomingTokens.size() > 2 ? upcomingTokens.get(2) : null);
    upcoming.put("then2", upcomingTokens.size() > 3 ? upcomingTokens.get(3) : null);
    out.put("upcoming", upcoming);

    out.put(
        "progress",
        Map.of(
            "completed", completed,
            "total", total,
            "pct", pct,
            "waiting", waiting.size()));

    if (tokenNo != null) {
      List<Map<String, Object>> before =
          buildPatientsBeforeYou(tokenNo, currentToken, waiting, all);
      out.put("yourStatus", buildYourStatus(tokenNo, currentToken, before.size(), nowLocal, before));
      out.put("waitingBeforeYou", before);
    }

    // Last 5 completed tokens
    List<Integer> lastCompleted =
        all.stream()
            .filter(q -> "COMPLETED".equals(q.getStatus()) && q.getTokenNo() != null)
            .map(DoctorQueueEntity::getTokenNo)
            .sorted(java.util.Comparator.reverseOrder())
            .limit(5)
            .toList();
    out.put("lastCompletedTokens", lastCompleted);

    // Emergency cases (reason contains EMERGENCY on visit — surfaced via patient name marker)
    List<Map<String, Object>> emergency = new ArrayList<>();
    for (DoctorQueueEntity q : all) {
      if ("WAITING".equals(q.getStatus()) || "RUNNING".equals(q.getStatus())) {
        String name = q.getPatientName() != null ? q.getPatientName().toUpperCase() : "";
        if (name.contains("EMERGENCY") || name.contains("URGENT")) {
          emergency.add(
              Map.of(
                  "token", q.getTokenNo(),
                  "patient", nullTo(q.getPatientName(), "Emergency"),
                  "status", q.getStatus()));
        }
      }
    }
    out.put("emergencyCases", emergency);

    return out;
  }

  /**
   * Tokens strictly between the current running token and the patient's token.
   * Example: current=11, yourToken=13 → shows 12. current=7, yourToken=15 → 8..14.
   * If nobody is RUNNING yet, only real WAITING tokens lower than yours are listed.
   */
  private List<Map<String, Object>> buildPatientsBeforeYou(
      int patientToken,
      int currentToken,
      List<DoctorQueueEntity> waiting,
      List<DoctorQueueEntity> all) {
    Map<Integer, DoctorQueueEntity> byToken = new HashMap<>();
    for (DoctorQueueEntity q : all) {
      if (q.getTokenNo() != null) {
        byToken.put(q.getTokenNo(), q);
      }
    }

    List<Map<String, Object>> before = new ArrayList<>();

    if (currentToken > 0) {
      // Spec: patients before you = tokens after current running, before your token
      for (int t = currentToken + 1; t < patientToken; t++) {
        DoctorQueueEntity q = byToken.get(t);
        if (q != null && ("COMPLETED".equals(q.getStatus()) || "CANCELLED".equals(q.getStatus()))) {
          continue;
        }
        String patient =
            q != null ? nullTo(q.getPatientName(), "Token " + t) : "Token " + t;
        before.add(Map.of("token", t, "patient", patient, "status", "Waiting"));
      }
      return before;
    }

    // No RUNNING token — list actual WAITING patients with lower token numbers
    for (DoctorQueueEntity w : waiting) {
      if (w.getTokenNo() != null && w.getTokenNo() < patientToken) {
        before.add(
            Map.of(
                "token", w.getTokenNo(),
                "patient", nullTo(w.getPatientName(), "Patient"),
                "status", "Waiting"));
      }
    }
    return before;
  }

  private Map<String, Object> buildYourStatus(
      int patientToken,
      int currentRunningToken,
      int patientsBefore,
      LocalTime nowLocal,
      List<Map<String, Object>> before) {
    int waitMinutes = patientsBefore * avgConsultationMinutes;
    LocalTime expected = nowLocal.plusMinutes(waitMinutes);

    List<Integer> beforeTokens = new ArrayList<>();
    for (Map<String, Object> row : before) {
      Object t = row.get("token");
      if (t instanceof Number n) {
        beforeTokens.add(n.intValue());
      }
    }

    Map<String, Object> yours = new HashMap<>();
    yours.put("token", patientToken);
    yours.put("currentRunningToken", currentRunningToken > 0 ? currentRunningToken : null);
    yours.put("patientsBefore", patientsBefore);
    yours.put("patientsBeforeTokens", beforeTokens);
    yours.put(
        "patientsBeforeDisplay",
        beforeTokens.isEmpty()
            ? "None"
            : beforeTokens.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("None"));
    yours.put("avgMinutes", avgConsultationMinutes);
    yours.put("waitMinutes", waitMinutes);
    yours.put("expectedTurnTime", expected.format(TIME_FMT));
    return yours;
  }

  private void notifyTwoAhead(Long hospitalId, String doctorId, LocalDate day, String doctorName) {
    List<DoctorQueueEntity> waiting =
        queueRepo.findByHospitalIdAndDoctorIdAndQueueDateAndStatusOrderByTokenNoAsc(
            hospitalId, doctorId, day, "WAITING");
    for (int i = 0; i < waiting.size(); i++) {
      int ahead = i; // index 0 = next (0 ahead of waiting line after current)
      // "only 3 patients remain before" → patient at index 3 has 3 ahead in waiting line
      if (ahead != 3) {
        continue;
      }
      DoctorQueueEntity q = waiting.get(i);
      if (q.isSmsTwoAheadSent()) {
        continue;
      }
      try {
        NotifyRequest req =
            new NotifyRequest(
                String.valueOf(hospitalId),
                q.getVisitId(),
                q.getPatientId(),
                "QUEUE_THREE_AHEAD",
                q.getPatientName(),
                q.getPatientPhone(),
                null,
                true,
                false,
                doctorName != null ? doctorName : q.getDoctorName(),
                defaultClinicName,
                Instant.now().toString(),
                q.getTokenNo() == null ? "—" : String.valueOf(q.getTokenNo()),
                0,
                q.getStatus(),
                null,
                null,
                "INR",
                Map.of("patientsAhead", "3"));
        notificationAppService.send(req);
        q.setSmsTwoAheadSent(true);
        queueRepo.save(q);
      } catch (Exception ignored) {
        // keep queue flow resilient
      }
    }
  }

  private boolean isDoctorAvailable(String doctorId) {
    Instant now = Instant.now();
    List<DoctorAvailabilityEntity> blocks =
        availabilityRepo.findByDoctorIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(
            doctorId, now, now);
    for (DoctorAvailabilityEntity b : blocks) {
      String t = b.getAvailabilityType();
      if (t != null
          && (t.equalsIgnoreCase("BUSY")
              || t.equalsIgnoreCase("LEAVE")
              || t.equalsIgnoreCase("UNAVAILABLE"))) {
        return false;
      }
    }
    return true;
  }

  public static Long resolveHospitalId(String clinicId) {
    if (clinicId == null || clinicId.isBlank()) {
      return 10001L;
    }
    try {
      return Long.parseLong(clinicId.trim());
    } catch (NumberFormatException ex) {
      return 10001L;
    }
  }

  private static String nullTo(String v, String d) {
    return v == null || v.isBlank() ? d : v;
  }
}
