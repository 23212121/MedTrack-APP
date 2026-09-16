package com.medtrack.booking.web;

import com.medtrack.booking.service.HospitalPatientListService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/hospital/patient-list")
public class HospitalPatientListController {
  private final HospitalPatientListService service;

  public HospitalPatientListController(HospitalPatientListService service) {
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader) {
    return service.list(requireHospitalId(hospitalHeader));
  }

  private static Long requireHospitalId(String header) {
    if (header == null || header.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hospital id is required");
    }
    try {
      return Long.parseLong(header.trim());
    } catch (NumberFormatException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hospital id is invalid");
    }
  }
}
