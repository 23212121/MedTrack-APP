package com.medtrack.booking.web;

import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.service.AppointmentAppService;
import com.medtrack.booking.service.AppointmentEnrichmentService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
  private final AppointmentAppService service;
  private final AppointmentEnrichmentService enrichment;

  public AppointmentController(
      AppointmentAppService service, AppointmentEnrichmentService enrichment) {
    this.service = service;
    this.enrichment = enrichment;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(value = "doctorId", required = false) String doctorId,
      @RequestParam(value = "patientId", required = false) String patientId,
      @RequestParam(value = "hospitalId", required = false) Long hospitalId,
      @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader,
      @RequestHeader(value = "X-Doctor-Id", required = false) String doctorHeader,
      @RequestHeader(value = "X-Login-Type", required = false) String loginType) {
    List<AppointmentEntity> rows;
    Long resolvedHospital = hospitalId != null ? hospitalId : parseLong(hospitalHeader);
    String resolvedDoctor =
        doctorId != null && !doctorId.isBlank()
            ? doctorId.trim()
            : ("USER".equalsIgnoreCase(loginType) && doctorHeader != null && !doctorHeader.isBlank()
                ? doctorHeader.trim()
                : null);
    if (resolvedDoctor != null && !resolvedDoctor.isBlank()) {
      rows = service.findByDoctor(resolvedDoctor, date);
    } else if (patientId != null && !patientId.isBlank()) {
      rows = service.findByPatientId(patientId);
    } else if (resolvedHospital != null) {
      rows = service.findByHospital(resolvedHospital);
    } else {
      rows = service.findAll();
    }
    List<Map<String, Object>> enriched = rows.stream().map(enrichment::enrichAppointment).toList();
    return Map.of("count", enriched.size(), "appointments", enriched);
  }

  private static Long parseLong(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
