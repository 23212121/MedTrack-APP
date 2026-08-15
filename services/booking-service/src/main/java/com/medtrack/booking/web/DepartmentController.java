package com.medtrack.booking.web;

import com.medtrack.booking.dto.DepartmentRequest;
import com.medtrack.booking.service.DepartmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * User/department API (booking-service). Maps to table {@code user}.
 * {@code doctorId} is FK → doctor_details.doctor_id.
 *
 * <pre>
 * GET    /api/departments
 * GET    /api/departments/{id}
 * GET    /api/departments?doctorId=DOC-XXXXXXXX
 * GET    /api/departments?hospitalId=...
 * POST   /api/departments
 * PUT    /api/departments/{id}
 * DELETE /api/departments/{id}
 * </pre>
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
  private final DepartmentService service;

  public DepartmentController(DepartmentService service) {
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(value = "doctorId", required = false) String doctorId,
      @RequestParam(value = "hospitalId", required = false) Long hospitalId) {
    List<Map<String, Object>> departments;
    if (doctorId != null && !doctorId.isBlank()) {
      departments = service.findByDoctorId(doctorId);
    } else if (hospitalId != null) {
      departments = service.findByHospitalId(hospitalId);
    } else {
      departments = service.findAll();
    }
    return Map.of("count", departments.size(), "departments", departments);
  }

  @GetMapping("/{id}")
  public Map<String, Object> getById(@PathVariable("id") String id) {
    return Map.of("department", service.findById(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@Valid @RequestBody DepartmentRequest request) {
    return Map.of(
        "message", "Department saved",
        "department", service.create(request));
  }

  @PutMapping("/{id}")
  public Map<String, Object> update(
      @PathVariable("id") String id, @Valid @RequestBody DepartmentRequest request) {
    return Map.of(
        "message", "Department updated",
        "department", service.update(id, request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable("id") String id) {
    service.delete(id);
  }
}
