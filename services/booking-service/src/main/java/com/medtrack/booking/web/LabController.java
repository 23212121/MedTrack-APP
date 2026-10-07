package com.medtrack.booking.web;

import com.medtrack.booking.domain.LabTechnicianEntity;
import com.medtrack.booking.service.LabAppService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class LabController {
  private final LabAppService service;

  public LabController(LabAppService service) {
    this.service = service;
  }

  @GetMapping("/api/labs")
  public Map<String, Object> list(
      @RequestParam(value = "hospitalId", required = false) Long hospitalId,
      @RequestParam(value = "activeOnly", defaultValue = "false") boolean activeOnly,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader) {
    Long hid = hospitalId != null ? hospitalId : parseLong(hospitalHeader);
    List<Map<String, Object>> rows = service.list(hid, activeOnly);
    return Map.of("count", rows.size(), "labs", rows);
  }

  @GetMapping("/api/labs/{id}")
  public Map<String, Object> get(@PathVariable("id") String id) {
    return Map.of("lab", service.get(id));
  }

  @PostMapping("/api/labs")
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

  @PostMapping("/api/labs/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> selfRegister(@RequestBody Map<String, Object> body) {
    return service.register(null, body, "self-register");
  }

  @PutMapping("/api/labs/{id}/status")
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

  @GetMapping("/api/lab/me")
  public Map<String, Object> me(
      @RequestHeader(value = "X-Lab-Id", required = false) String labHeader,
      @RequestHeader(value = "X-User", required = false) String userHeader) {
    return service.currentLab(service.requireByHeader(labHeader, userHeader));
  }

  @GetMapping("/api/lab/patient-details")
  public Map<String, Object> patientDetails(
      @RequestParam(value = "phone", required = false) String phone,
      @RequestParam(value = "aadhaar", required = false) String aadhaar,
      @RequestHeader(value = "X-Lab-Id", required = false) String labHeader,
      @RequestHeader(value = "X-User", required = false) String userHeader) {
    service.requireByHeader(labHeader, userHeader);
    return service.lookupPatient(phone, aadhaar);
  }

  @GetMapping("/api/lab/patients")
  public Map<String, Object> patients(
      @RequestParam(value = "phone", required = false) String phone,
      @RequestParam(value = "aadhaar", required = false) String aadhaar,
      @RequestParam(value = "name", required = false) String name,
      @RequestHeader(value = "X-Lab-Id", required = false) String labHeader,
      @RequestHeader(value = "X-User", required = false) String userHeader) {
    service.requireByHeader(labHeader, userHeader);
    return service.searchPatients(phone, aadhaar, name);
  }

  @GetMapping("/api/lab/reports")
  public Map<String, Object> reports(
      @RequestHeader(value = "X-Lab-Id", required = false) String labHeader,
      @RequestHeader(value = "X-User", required = false) String userHeader) {
    LabTechnicianEntity lab = service.requireByHeader(labHeader, userHeader);
    return service.listUploads(lab);
  }

  @PostMapping(value = "/api/lab/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> upload(
      @RequestParam(value = "patientName", required = false) String patientName,
      @RequestParam(value = "phone", required = false) String phone,
      @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
      @RequestParam(value = "aadhaar", required = false) String aadhaar,
      @RequestParam(value = "aadhaarNumber", required = false) String aadhaarNumber,
      @RequestParam(value = "title", required = false) String title,
      @RequestParam(value = "notes", required = false) String notes,
      @RequestParam(value = "age", required = false) Integer age,
      @RequestParam(value = "gender", required = false) String gender,
      @RequestParam(value = "files", required = false) MultipartFile[] files,
      @RequestHeader(value = "X-Lab-Id", required = false) String labHeader,
      @RequestHeader(value = "X-User", required = false) String userHeader) {
    LabTechnicianEntity lab = service.requireByHeader(labHeader, userHeader);
    return service.uploadReports(
        lab,
        patientName,
        first(phone, phoneNumber),
        first(aadhaar, aadhaarNumber),
        title,
        notes,
        age,
        gender,
        files,
        userHeader);
  }

  private static String first(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return b;
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
