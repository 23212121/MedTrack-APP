package com.medtrack.booking.web;

import com.medtrack.booking.service.PatientSearchService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
public class PatientSearchController {
  private final PatientSearchService service;

  public PatientSearchController(PatientSearchService service) {
    this.service = service;
  }

  @GetMapping("/search")
  public Map<String, Object> search(@RequestParam String name) {
    return service.searchByName(name);
  }
}
