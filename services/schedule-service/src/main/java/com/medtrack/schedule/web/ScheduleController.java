package com.medtrack.schedule.web;

import com.medtrack.common.dto.ScheduleDayChart;
import com.medtrack.schedule.domain.DoctorAvailabilityEntity;
import com.medtrack.schedule.domain.DoctorScheduleEntity;
import com.medtrack.schedule.domain.FeeRuleEntity;
import com.medtrack.schedule.service.ScheduleAppService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
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

  @PutMapping("/weekly/{doctorId}")
  public Map<String, Object> replaceWeekly(
      @PathVariable String doctorId, @RequestBody List<WeeklySlotRequest> body) {
    return Map.of("schedules", service.replaceWeekly(doctorId, body));
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

  /** Booking helper: from/to date filter of when doctor is available. */
  @GetMapping("/available-days/{doctorId}")
  public Map<String, Object> availableDays(
      @PathVariable String doctorId,
      @RequestParam String from,
      @RequestParam String to) {
    return service.availableDays(doctorId, from, to);
  }

  @PostMapping("/availability")
  public DoctorAvailabilityEntity addAvailability(@RequestBody AvailabilityRequest body) {
    return service.addAvailability(body);
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
      Integer slotMinutes) {}

  public record AvailabilityRequest(
      @NotBlank String doctorId,
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
