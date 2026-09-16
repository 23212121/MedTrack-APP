package com.medtrack.schedule.web;

import com.medtrack.common.dto.ScheduleDayChart;
import com.medtrack.schedule.domain.DoctorAvailabilityEntity;
import com.medtrack.schedule.domain.DoctorScheduleEntity;
import com.medtrack.schedule.domain.FeeRuleEntity;
import com.medtrack.schedule.service.ScheduleAppService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {
  private final ScheduleAppService service;

  public ScheduleController(ScheduleAppService service) {
    this.service = service;
  }

  @GetMapping("/weekly/{doctorId}")
  public Map<String, Object> weekly(@PathVariable String doctorId) {
    return Map.of("schedules", service.weekly(doctorId));
  }

  @GetMapping("/hospital/{hospitalId}")
  public Map<String, Object> hospitalWeekly(
      @PathVariable Long hospitalId,
      @RequestParam(value = "personIds", required = false) String personIds) {
    return Map.of("schedules", service.hospitalWeekly(hospitalId, splitIds(personIds)));
  }

  @PutMapping("/weekly/{doctorId}")
  public Map<String, Object> replaceWeekly(
      @PathVariable String doctorId,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader,
      @RequestBody List<WeeklySlotRequest> body) {
    return Map.of("schedules", service.replaceWeekly(doctorId, parseHospitalId(hospitalHeader), body));
  }

  @PutMapping("/weekly-batch")
  public Map<String, Object> replaceWeeklyBatch(
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader,
      @RequestBody WeeklyBatchRequest body) {
    Long hospitalId =
        body.hospitalId() != null ? body.hospitalId() : parseHospitalId(hospitalHeader);
    return Map.of(
        "schedules", service.replaceWeeklyForMany(body.personIds(), hospitalId, body.schedules()));
  }

  @GetMapping("/availability/{doctorId}")
  public Map<String, Object> availability(
      @PathVariable String doctorId,
      @RequestParam String from,
      @RequestParam String to) {
    return Map.of(
        "availability",
        service.availability(doctorId, Instant.parse(from), Instant.parse(to)));
  }

  @GetMapping("/availability-hospital/{hospitalId}")
  public Map<String, Object> hospitalAvailability(
      @PathVariable Long hospitalId,
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(value = "personIds", required = false) String personIds) {
    return Map.of(
        "availability",
        service.hospitalAvailability(
            hospitalId, splitIds(personIds), Instant.parse(from), Instant.parse(to)));
  }

  /** Booking helper: from/to date filter of when doctor is available. */
  @GetMapping("/available-days/{doctorId}")
  public Map<String, Object> availableDays(
      @PathVariable String doctorId,
      @RequestParam String from,
      @RequestParam String to) {
    return service.availableDays(doctorId, from, to);
  }

  @PostMapping("/availability")
  public DoctorAvailabilityEntity addAvailability(
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader,
      @RequestBody AvailabilityRequest body) {
    Long hospitalId =
        body.hospitalId() != null ? body.hospitalId() : parseHospitalId(hospitalHeader);
    return service.addAvailability(
        new AvailabilityRequest(
            body.doctorId(),
            hospitalId,
            body.startsAt(),
            body.endsAt(),
            body.availabilityType(),
            body.reason()));
  }

  @PostMapping("/availability-batch")
  public Map<String, Object> addAvailabilityBatch(
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader,
      @RequestBody AvailabilityBatchRequest body) {
    Long hospitalId =
        body.hospitalId() != null ? body.hospitalId() : parseHospitalId(hospitalHeader);
    return Map.of(
        "availability",
        service.addAvailabilityForMany(
            new AvailabilityBatchRequest(
                body.personIds(),
                hospitalId,
                body.startsAt(),
                body.endsAt(),
                body.availabilityType(),
                body.reason())));
  }

  @DeleteMapping("/availability/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAvailability(@PathVariable String id) {
    service.deleteAvailability(id);
  }

  @GetMapping("/chart/{doctorId}")
  public ScheduleDayChart chart(
      @PathVariable String doctorId,
      @RequestParam String date,
      @RequestParam(required = false) String doctorName) {
    return service.dayChart(doctorId, doctorName, date);
  }

  @GetMapping("/fees/{doctorId}")
  public FeeRuleEntity fees(@PathVariable String doctorId) {
    return service.feeRule(doctorId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee rule not found"));
  }

  @PutMapping("/fees/{doctorId}")
  public FeeRuleEntity upsertFees(@PathVariable String doctorId, @RequestBody FeeRuleRequest body) {
    return service.upsertFeeRule(doctorId, body);
  }

  @PostMapping("/fees/calculate")
  public Map<String, Object> calculate(@RequestBody FeeCalcRequest body) {
    return service.calculateOvertime(body);
  }

  public record WeeklySlotRequest(
      @NotNull Integer dayOfWeek,
      @NotBlank String startTime,
      @NotBlank String endTime,
      Integer slotMinutes,
      Long hospitalId) {}

  public record WeeklyBatchRequest(
      @NotEmpty List<String> personIds,
      Long hospitalId,
      @NotNull List<WeeklySlotRequest> schedules) {}

  private static List<String> splitIds(String csv) {
    if (csv == null || csv.isBlank()) return List.of();
    return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  private static Long parseHospitalId(String header) {
    if (header == null || header.isBlank()) return null;
    try {
      return Long.parseLong(header.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  public record AvailabilityRequest(
      @NotBlank String doctorId,
      Long hospitalId,
      @NotBlank String startsAt,
      @NotBlank String endsAt,
      String availabilityType,
      String reason) {}

  public record AvailabilityBatchRequest(
      @NotEmpty List<String> personIds,
      Long hospitalId,
      @NotBlank String startsAt,
      @NotBlank String endsAt,
      String availabilityType,
      String reason) {}

  public record FeeRuleRequest(
      String clinicId,
      Double baseConsultFee,
      Integer fixedConsultMinutes,
      Double overtimeFeeAmount,
      Integer overtimeFeePerBlockMinutes,
      String currency) {}

  public record FeeCalcRequest(
      String doctorId, int actualConsultMinutes) {}
}
