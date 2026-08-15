package com.medtrack.booking.web;

import com.medtrack.booking.service.DoctorPortalService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/doctor")
public class DoctorPortalController {
  private final DoctorPortalService service;

  public DoctorPortalController(DoctorPortalService service) {
    this.service = service;
  }

  /**
   * Same-day patient list / token tiles. Requires matching hospital + doctor (session headers).
   * Other doctors' appointments are never returned.
   */
  @GetMapping("/patients")
  public Map<String, Object> patients(
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader,
      @RequestHeader(value = "X-Doctor-Id", required = false) String doctorHeader,
      @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    Long hospitalId = requireHospitalId(hospitalHeader);
    String doctorId = requireDoctorId(doctorHeader);
    return service.todayPatients(hospitalId, doctorId, date);
  }

  /** Popup detail for a token tile: patient fields + same-hospital uploaded documents. */
  @GetMapping("/patients/{appointmentId}")
  public Map<String, Object> patientDetail(
      @PathVariable("appointmentId") String appointmentId,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader,
      @RequestHeader(value = "X-Doctor-Id", required = false) String doctorHeader) {
    Long hospitalId = requireHospitalId(hospitalHeader);
    String doctorId = requireDoctorId(doctorHeader);
    return service.patientDetail(hospitalId, doctorId, appointmentId);
  }

  private static Long requireHospitalId(String header) {
    if (header == null || header.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hospital id is required");
    }
    try {
      return Long.parseLong(header.trim());
    } catch (NumberFormatException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid hospital id");
    }
  }

  private static String requireDoctorId(String header) {
    if (header == null || header.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Doctor id is required");
    }
    return header.trim();
  }
}
