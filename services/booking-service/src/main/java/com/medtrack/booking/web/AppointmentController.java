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
          LocalDate date) {
    List<AppointmentEntity> rows;
    if (doctorId != null && !doctorId.isBlank()) {
      rows = service.findByDoctor(doctorId, date);
    } else if (patientId != null && !patientId.isBlank()) {
      rows = service.findByPatientId(patientId);
    } else if (hospitalId != null) {
      rows = service.findByHospital(hospitalId);
    } else {
      rows = service.findAll();
    }
    List<Map<String, Object>> enriched = rows.stream().map(enrichment::enrichAppointment).toList();
    return Map.of("count", enriched.size(), "appointments", enriched);
  }
}
