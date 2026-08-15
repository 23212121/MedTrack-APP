package com.medtrack.booking.web;

import com.medtrack.booking.service.HospitalRegistrationService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hospitals")
public class HospitalController {
  private final HospitalRegistrationService service;

  public HospitalController(HospitalRegistrationService service) {
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> list() {
    List<Map<String, Object>> hospitals = service.list();
    return Map.of("count", hospitals.size(), "hospitals", hospitals);
  }

  @GetMapping("/next-id")
  public Map<String, Object> nextId() {
    return Map.of("nextHospitalId", service.nextHospitalId());
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable Long id) {
    return Map.of("hospital", service.get(id));
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> register(@RequestBody Map<String, Object> body) {
    return service.register(body);
  }
}
