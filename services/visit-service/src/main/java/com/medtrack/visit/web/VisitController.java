package com.medtrack.visit.web;

import com.medtrack.visit.domain.VisitEntity;
import com.medtrack.visit.service.VisitAppService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/visits")
public class VisitController {
  private final VisitAppService service;

  public VisitController(VisitAppService service) {
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(required = false) String doctorId,
      @RequestParam(required = false) String date) {
    return Map.of("visits", service.list(doctorId, date));
  }

  @GetMapping("/check-in-board")
  public Map<String, Object> board(
      @RequestParam(value = "hospitalId", required = false) String hospitalId,
      @RequestParam(value = "clinicId", required = false) String clinicId,
      @RequestParam(value = "doctorId", required = false) String doctorId) {
    String hospital = hospitalId != null && !hospitalId.isBlank() ? hospitalId : clinicId;
    return Map.of("visits", service.checkInBoard(hospital, doctorId));
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable String id) {
    return service.detail(id);
  }

  @PostMapping
  public VisitEntity create(@RequestBody Map<String, Object> body) {
    return service.create(body);
  }

  @PostMapping("/{id}/check-in")
  public VisitEntity checkIn(@PathVariable String id) {
    return service.checkIn(id);
  }

  @PostMapping("/{id}/start")
  public VisitEntity start(@PathVariable String id) {
    return service.startConsult(id);
  }

  @PostMapping("/{id}/late")
  public VisitEntity late(@PathVariable String id, @RequestBody(required = false) Map<String, Integer> body) {
    int minutes = body != null && body.get("minutes") != null ? body.get("minutes") : 15;
    return service.markLate(id, minutes);
  }

  @PostMapping("/{id}/complete")
  public VisitEntity complete(@PathVariable String id) {
    return service.complete(id);
  }
}
