package com.medtrack.booking.web;

import com.medtrack.booking.service.MedicalStoreAppService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/medical-stores")
public class MedicalStoreController {
  private final MedicalStoreAppService service;

  public MedicalStoreController(MedicalStoreAppService service) {
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(value = "hospitalId", required = false) Long hospitalId,
      @RequestParam(value = "activeOnly", defaultValue = "false") boolean activeOnly,
      @RequestParam(value = "allHospitals", defaultValue = "false") boolean allHospitals,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader,
      @RequestHeader(value = "X-Login-Type", required = false) String loginType) {
    boolean directory =
        allHospitals || (loginType != null && "PATIENT".equalsIgnoreCase(loginType.trim()));
    Long hid = directory ? hospitalId : (hospitalId != null ? hospitalId : parseLong(hospitalHeader));
    List<Map<String, Object>> rows = service.list(hid, activeOnly);
    return Map.of("count", rows.size(), "stores", rows);
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable("id") String id) {
    return Map.of("store", service.get(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> register(
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader,
      @RequestHeader(value = "X-User", required = false) String user) {
    Long hospitalId = parseLong(hospitalHeader);
    if (hospitalId == null && body.get("hospitalId") != null) {
      hospitalId = parseLong(String.valueOf(body.get("hospitalId")));
    }
    return service.register(hospitalId, body, user);
  }

  @PutMapping("/{id}/status")
  public Map<String, Object> status(
      @PathVariable("id") String id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader,
      @RequestHeader(value = "X-User", required = false) String user) {
    Object st = body.get("status");
    if (st == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
    }
    return service.setStatus(id, parseLong(hospitalHeader), String.valueOf(st), user);
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
