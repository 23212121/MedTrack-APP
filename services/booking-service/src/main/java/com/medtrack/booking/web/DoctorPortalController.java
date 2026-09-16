package com.medtrack.booking.web;

import com.medtrack.booking.service.DoctorPortalIdentityService;
import com.medtrack.booking.service.DoctorPortalService;
import com.medtrack.booking.service.DoctorSession;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/doctor")
public class DoctorPortalController {
  private final DoctorPortalService service;
  private final DoctorPortalIdentityService identity;

  public DoctorPortalController(
      DoctorPortalService service, DoctorPortalIdentityService identity) {
    this.service = service;
    this.identity = identity;
  }

  /**
   * Dashboard + same-day patient list. Doctor and hospital come from the login token,
   * then bookings are filtered to that assignment only.
   */
  @GetMapping({"/patients", "/dashboard"})
  public Map<String, Object> patients(
      HttpServletRequest request,
      @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    DoctorSession doctor = identity.requireDoctor(request);
    return service.todayPatients(doctor.hospitalId(), doctor.doctorId(), date);
  }

  /** Popup detail for a token tile: patient fields + same-hospital uploaded documents. */
  @GetMapping("/patients/{appointmentId}")
  public Map<String, Object> patientDetail(
      HttpServletRequest request, @PathVariable("appointmentId") String appointmentId) {
    DoctorSession doctor = identity.requireDoctor(request);
    return service.patientDetail(doctor.hospitalId(), doctor.doctorId(), appointmentId);
  }

  /** BOOKED → IN-PROCESS (start) → COMPLETED. */
  @PatchMapping("/patients/{appointmentId}/status")
  public Map<String, Object> updateStatus(
      HttpServletRequest request,
      @PathVariable("appointmentId") String appointmentId,
      @RequestBody Map<String, Object> body) {
    DoctorSession doctor = identity.requireDoctor(request);
    Object action = body == null ? null : body.get("action");
    if (action == null && body != null) {
      action = body.get("status");
    }
    return service.updateStatus(
        doctor.hospitalId(),
        doctor.doctorId(),
        appointmentId,
        action == null ? "" : String.valueOf(action));
  }

  /** Doctor RX / patient document upload — scoped to this doctor's assigned booking. */
  @PostMapping(
      value = "/patients/{appointmentId}/documents",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> uploadDocuments(
      HttpServletRequest request,
      @PathVariable("appointmentId") String appointmentId,
      @RequestParam("aadhaarNumber") String aadhaarNumber,
      @RequestParam(value = "files", required = false) MultipartFile[] files) {
    DoctorSession doctor = identity.requireDoctor(request);
    String uploadedBy = request.getHeader("X-User");
    if (uploadedBy == null || uploadedBy.isBlank()) {
      uploadedBy = doctor.doctorId();
    }
    return service.uploadPatientDocuments(
        doctor.hospitalId(),
        doctor.doctorId(),
        appointmentId,
        aadhaarNumber,
        files,
        uploadedBy);
  }
}
