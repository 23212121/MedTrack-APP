package com.medtrack.schedule.service;

import com.medtrack.common.dto.ScheduleDayChart;
import com.medtrack.common.port.VisitChartPort;
import com.medtrack.schedule.domain.DoctorAvailabilityEntity;
import com.medtrack.schedule.domain.DoctorScheduleEntity;
import com.medtrack.schedule.domain.FeeRuleEntity;
import com.medtrack.schedule.repo.DoctorAvailabilityRepository;
import com.medtrack.schedule.repo.DoctorScheduleRepository;
import com.medtrack.schedule.repo.FeeRuleRepository;
import com.medtrack.schedule.web.ScheduleController.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleAppService implements ApplicationRunner {
  private final DoctorScheduleRepository scheduleRepo;
  private final DoctorAvailabilityRepository availabilityRepo;
  private final FeeRuleRepository feeRepo;
  private final VisitChartPort visitChartPort;

  @Value("${medtrack.clinic-id}")
  private String clinicId;
  @Value("${medtrack.doctor-id}")
  private String seedDoctorId;
  @Value("${medtrack.doctor-name}")
  private String seedDoctorName;
  @Value("${medtrack.seed-hospital-id:10001}")
  private Long seedHospitalId;

  public ScheduleAppService(
      DoctorScheduleRepository scheduleRepo,
      DoctorAvailabilityRepository availabilityRepo,
      FeeRuleRepository feeRepo,
      @Lazy VisitChartPort visitChartPort) {
    this.scheduleRepo = scheduleRepo;
    this.availabilityRepo = availabilityRepo;
    this.feeRepo = feeRepo;
    this.visitChartPort = visitChartPort;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      if (scheduleRepo.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(seedDoctorId).isEmpty()) {
        for (int day = 1; day <= 5; day++) {
          DoctorScheduleEntity s = new DoctorScheduleEntity();
          s.setDoctorId(seedDoctorId);
          s.setHospitalId(seedHospitalId);
          s.setDayOfWeek(day);
          s.setStartTime("09:00");
          s.setEndTime("13:00");
          s.setSlotMinutes(15);
          scheduleRepo.save(s);
          DoctorScheduleEntity afternoon = new DoctorScheduleEntity();
          afternoon.setDoctorId(seedDoctorId);
          afternoon.setHospitalId(seedHospitalId);
          afternoon.setDayOfWeek(day);
          afternoon.setStartTime("14:00");
          afternoon.setEndTime("18:00");
          afternoon.setSlotMinutes(15);
          scheduleRepo.save(afternoon);
        }
      }
      if (feeRepo.findByDoctorId(seedDoctorId).isEmpty()) {
        FeeRuleEntity fee = new FeeRuleEntity();
        fee.setId("fee-" + seedDoctorId);
        fee.setClinicId(clinicId);
        fee.setDoctorId(seedDoctorId);
        fee.setBaseConsultFee(500);
        fee.setFixedConsultMinutes(15);
        fee.setOvertimeFeeAmount(200);
        fee.setOvertimeFeePerBlockMinutes(15);
        fee.setCurrency("INR");
        feeRepo.save(fee);
      }
    } catch (Exception ex) {
      // Seed is best-effort — do not block app startup (e.g. leave APIs).
      System.err.println("Schedule seed skipped: " + ex.getMessage());
    }
  }

  public List<DoctorScheduleEntity> weekly(String doctorId) {
    return scheduleRepo.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId);
  }

  @Transactional
  public List<DoctorScheduleEntity> replaceWeekly(String doctorId, List<WeeklySlotRequest> body) {
    scheduleRepo.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId)
        .forEach(scheduleRepo::delete);
    scheduleRepo.flush();
    List<DoctorScheduleEntity> saved = new ArrayList<>();
    for (WeeklySlotRequest req : body) {
      DoctorScheduleEntity s = new DoctorScheduleEntity();
      s.setDoctorId(doctorId);
      s.setDayOfWeek(req.dayOfWeek());
      s.setStartTime(req.startTime());
      s.setEndTime(req.endTime());
      s.setSlotMinutes(req.slotMinutes() == null ? 15 : req.slotMinutes());
      saved.add(scheduleRepo.save(s));
    }
    return saved;
  }

  public List<DoctorAvailabilityEntity> availability(String doctorId, Instant from, Instant to) {
    return availabilityRepo.findByDoctorIdAndStartsAtBetweenOrderByStartsAtAsc(doctorId, from, to);
  }

  /**
   * For booking UI: from/to date filter showing when the doctor is available
   * (weekly working hours minus BUSY/LEAVE/BLOCKED blocks).
   */
  public Map<String, Object> availableDays(String doctorId, String fromDate, String toDate) {
    LocalDate from = LocalDate.parse(fromDate);
    LocalDate to = LocalDate.parse(toDate);
    if (to.isBefore(from)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "to date must be on or after from date");
    }
    if (from.plusDays(62).isBefore(to)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Date range cannot exceed 62 days");
    }

    ZoneId zone = ZoneId.of("Asia/Kolkata");
    Instant rangeStart = from.atStartOfDay(zone).toInstant();
    Instant rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
    List<DoctorScheduleEntity> weekly = weekly(doctorId);
    List<DoctorAvailabilityEntity> blocks =
        availabilityRepo.findByDoctorIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(
            doctorId, rangeEnd, rangeStart);

    List<Map<String, Object>> days = new ArrayList<>();
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      int dow = day.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : day.getDayOfWeek().getValue() % 7;
      List<Map<String, String>> windows = new ArrayList<>();
      for (DoctorScheduleEntity s : weekly) {
        if (s.getDayOfWeek() != dow) continue;
        windows.add(
            Map.of(
                "start", s.getStartTime(),
                "end", s.getEndTime(),
                "slotMinutes", String.valueOf(s.getSlotMinutes())));
      }

      Instant dayStart = day.atStartOfDay(zone).toInstant();
      Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
      List<Map<String, String>> blocked = new ArrayList<>();
      boolean fullDayLeave = false;
      for (DoctorAvailabilityEntity a : blocks) {
        if (!a.getStartsAt().isBefore(dayEnd) || !a.getEndsAt().isAfter(dayStart)) {
          continue;
        }
        String type = a.getAvailabilityType() == null ? "" : a.getAvailabilityType().toUpperCase();
        if (!List.of("BUSY", "LEAVE", "BLOCKED", "UNAVAILABLE").contains(type)) {
          continue;
        }
        blocked.add(
            Map.of(
                "type", type,
                "reason", a.getReason() == null ? type : a.getReason(),
                "startsAt", a.getStartsAt().toString(),
                "endsAt", a.getEndsAt().toString()));
        // Leave covering most of the day → mark unavailable
        if ("LEAVE".equals(type) || "UNAVAILABLE".equals(type)) {
          Instant bs = a.getStartsAt().isBefore(dayStart) ? dayStart : a.getStartsAt();
          Instant be = a.getEndsAt().isAfter(dayEnd) ? dayEnd : a.getEndsAt();
          if (Duration.between(bs, be).toHours() >= 6) {
            fullDayLeave = true;
          }
        }
      }

      boolean available = !windows.isEmpty() && !fullDayLeave;
      Map<String, Object> row = new HashMap<>();
      row.put("date", day.toString());
      row.put("dayName", day.getDayOfWeek().name());
      row.put("available", available);
      row.put("windows", windows);
      row.put("blocked", blocked);
      row.put(
          "summary",
          !available
              ? (windows.isEmpty() ? "Off / no schedule" : "On leave / unavailable")
              : windows.stream()
                  .map(w -> w.get("start") + "–" + w.get("end"))
                  .reduce((a, b) -> a + ", " + b)
                  .orElse("Available"));
      days.add(row);
    }

    Map<String, Object> out = new HashMap<>();
    out.put("doctorId", doctorId);
    out.put("from", from.toString());
    out.put("to", to.toString());
    out.put("hasWeeklySchedule", !weekly.isEmpty());
    out.put("days", days);
    out.put(
        "availableDates",
        days.stream()
            .filter(d -> Boolean.TRUE.equals(d.get("available")))
            .map(d -> String.valueOf(d.get("date")))
            .toList());
    return out;
  }

  public DoctorAvailabilityEntity addAvailability(AvailabilityRequest body) {
    DoctorAvailabilityEntity e = new DoctorAvailabilityEntity();
    e.setDoctorId(body.doctorId());
    e.setStartsAt(Instant.parse(body.startsAt()));
    e.setEndsAt(Instant.parse(body.endsAt()));
    e.setAvailabilityType(
        body.availabilityType() == null ? "BUSY" : body.availabilityType().toUpperCase());
    e.setReason(body.reason());
    return availabilityRepo.save(e);
  }

  public void deleteAvailability(String id) {
    availabilityRepo.deleteById(id);
  }

  public Optional<FeeRuleEntity> feeRule(String doctorId) {
    return feeRepo.findByDoctorId(doctorId)
        .or(() -> feeRepo.findByClinicIdAndDoctorIdIsNull(clinicId));
  }

  public FeeRuleEntity upsertFeeRule(String doctorId, FeeRuleRequest body) {
    FeeRuleEntity fee = feeRepo.findByDoctorId(doctorId).orElseGet(FeeRuleEntity::new);
    if (fee.getId() == null) {
      fee.setId("fee-" + doctorId);
      fee.setDoctorId(doctorId);
    }
    fee.setClinicId(body.clinicId() != null ? body.clinicId() : clinicId);
    if (body.baseConsultFee() != null) fee.setBaseConsultFee(body.baseConsultFee());
    if (body.fixedConsultMinutes() != null) fee.setFixedConsultMinutes(body.fixedConsultMinutes());
    if (body.overtimeFeeAmount() != null) fee.setOvertimeFeeAmount(body.overtimeFeeAmount());
    if (body.overtimeFeePerBlockMinutes() != null) {
      fee.setOvertimeFeePerBlockMinutes(body.overtimeFeePerBlockMinutes());
    }
    if (body.currency() != null) fee.setCurrency(body.currency());
    return feeRepo.save(fee);
  }

  public Map<String, Object> calculateOvertime(String doctorId, int actualConsultMinutes) {
    return calculateOvertime(new FeeCalcRequest(doctorId, actualConsultMinutes));
  }

  public Map<String, Object> calculateOvertime(FeeCalcRequest body) {
    FeeRuleEntity rule = feeRule(body.doctorId())
        .orElseThrow(() -> new IllegalArgumentException("No fee rule"));
    int fixed = rule.getFixedConsultMinutes();
    int over = Math.max(0, body.actualConsultMinutes() - fixed);
    int blocks = over == 0 ? 0 : (int) Math.ceil(over / (double) rule.getOvertimeFeePerBlockMinutes());
    double overtimeFee = blocks * rule.getOvertimeFeeAmount();
    double total = rule.getBaseConsultFee() + overtimeFee;
    return Map.of(
        "baseFee", rule.getBaseConsultFee(),
        "fixedConsultMinutes", fixed,
        "actualConsultMinutes", body.actualConsultMinutes(),
        "overtimeMinutes", over,
        "overtimeBlocks", blocks,
        "overtimeFee", overtimeFee,
        "totalFee", total,
        "currency", rule.getCurrency());
  }

  public ScheduleDayChart dayChart(String doctorId, String doctorName, String date) {
    LocalDate day = LocalDate.parse(date);
    ZoneId zone = ZoneId.of("Asia/Kolkata");
    Instant dayStart = day.atStartOfDay(zone).toInstant();
    Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();

    List<DoctorScheduleEntity> weekly = weekly(doctorId);
    int dow = day.getDayOfWeek().getValue() % 7; // Sun=0 aligned with PRD
    if (day.getDayOfWeek() == DayOfWeek.SUNDAY) dow = 0;

    List<ScheduleDayChart.ChartBlock> blocks = new ArrayList<>();
    int availableMinutes = 0;

    for (DoctorScheduleEntity s : weekly) {
      if (s.getDayOfWeek() != dow) continue;
      LocalTime start = LocalTime.parse(s.getStartTime());
      LocalTime end = LocalTime.parse(s.getEndTime());
      availableMinutes += (int) Duration.between(start, end).toMinutes();
      blocks.add(new ScheduleDayChart.ChartBlock(
          day.atTime(start).atZone(zone).toInstant().toString(),
          day.atTime(end).atZone(zone).toInstant().toString(),
          "WORKING",
          "Working hours",
          null,
          null));
    }

    List<DoctorAvailabilityEntity> avail =
        availabilityRepo.findByDoctorIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(
            doctorId, dayEnd, dayStart);
    int busyMinutes = 0;
    for (DoctorAvailabilityEntity a : avail) {
      long mins = Duration.between(
          a.getStartsAt().isBefore(dayStart) ? dayStart : a.getStartsAt(),
          a.getEndsAt().isAfter(dayEnd) ? dayEnd : a.getEndsAt()).toMinutes();
      if ("BUSY".equalsIgnoreCase(a.getAvailabilityType())
          || "LEAVE".equalsIgnoreCase(a.getAvailabilityType())
          || "BLOCKED".equalsIgnoreCase(a.getAvailabilityType())) {
        busyMinutes += (int) Math.max(0, mins);
      }
      blocks.add(new ScheduleDayChart.ChartBlock(
          a.getStartsAt().toString(),
          a.getEndsAt().toString(),
          a.getAvailabilityType(),
          a.getReason() == null ? a.getAvailabilityType() : a.getReason(),
          null,
          null));
    }

    int bookedMinutes = 0;
    try {
      for (VisitChartPort.VisitChartItem v : visitChartPort.listForDoctorDate(doctorId, date)) {
        if (v.scheduledStart() == null) continue;
        Instant s = v.scheduledStart();
        Instant e = v.scheduledEnd() != null
            ? v.scheduledEnd()
            : s.plus(Duration.ofMinutes(15));
        bookedMinutes += (int) Duration.between(s, e).toMinutes();
        blocks.add(new ScheduleDayChart.ChartBlock(
            s.toString(),
            e.toString(),
            "BOOKED",
            v.patientName() != null ? v.patientName() : "Patient",
            v.id(),
            v.status()));
      }
    } catch (Exception ignored) {
      // visit module may not be ready during early startup
    }

    double util = availableMinutes == 0
        ? 0
        : Math.min(100.0, ((bookedMinutes + busyMinutes) * 100.0) / availableMinutes);

    return new ScheduleDayChart(
        doctorId,
        doctorName != null ? doctorName : seedDoctorName,
        date,
        blocks,
        availableMinutes,
        busyMinutes,
        bookedMinutes,
        Math.round(util * 10) / 10.0);
  }
}
