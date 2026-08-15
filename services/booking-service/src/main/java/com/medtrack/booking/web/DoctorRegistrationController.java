package com.medtrack.booking.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.medtrack.booking.service.DoctorSectionService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Wizard section APIs — each section is an independent table with doctor_id FK.
 *
 * <pre>
 * GET  /api/doctors?hospitalId=…   — doctors registered under a hospital
 * POST /api/doctors/init
 * GET  /api/doctors/{doctorId}/sections/{section}
 * PUT  /api/doctors/{doctorId}/sections/{section}
 * </pre>
 *
 * sections: personal | contact | professional | clinic | identity | bank | documents
 */
@RestController
@RequestMapping("/api/doctors")
public class DoctorRegistrationController {
  private final DoctorSectionService service;

  public DoctorRegistrationController(DoctorSectionService service) {
    this.service = service;
  }

  /**
   * List doctors. Pass {@code hospitalId} to limit to doctors registered under that hospital
   * (Book Appointment dropdown).
   */
  @GetMapping
  public Map<String, Object> list(
      @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
    List<Map<String, Object>> doctors = service.listDoctors(hospitalId);
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("count", doctors.size());
    body.put("doctors", doctors);
    if (hospitalId != null) body.put("hospitalId", hospitalId);
    return body;
  }

  /** Full-form doctor registration (stores hospitalId on doctor_clinic). */
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> register(@RequestBody JsonNode body) {
    return service.registerFlat(body);
  }

  @PostMapping("/init")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> init() {
    return service.init();
  }

  @GetMapping("/{doctorId}/sections/{section}")
  public Map<String, Object> getSection(
      @PathVariable("doctorId") String doctorId, @PathVariable("section") String section) {
    return Map.of("section", section, "data", service.getSection(doctorId, section));
  }

  @PutMapping("/{doctorId}/sections/{section}")
  public Map<String, Object> saveSection(
      @PathVariable("doctorId") String doctorId,
      @PathVariable("section") String section,
      @RequestBody JsonNode body) {
    return service.saveSection(doctorId, section, body);
  }
}
