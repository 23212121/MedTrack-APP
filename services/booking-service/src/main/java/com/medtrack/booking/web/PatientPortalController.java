package com.medtrack.booking.web;

import com.medtrack.booking.domain.PatientReportEntity;
import com.medtrack.booking.service.BookingAppService;
import com.medtrack.booking.service.DocumentUploadService;
import com.medtrack.booking.service.PatientPortalService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patient")
public class PatientPortalController {
  private final PatientPortalService portalService;
  private final BookingAppService bookingService;
  private final DocumentUploadService documentService;

  public PatientPortalController(
      PatientPortalService portalService,
      BookingAppService bookingService,
      DocumentUploadService documentService) {
    this.portalService = portalService;
    this.bookingService = bookingService;
    this.documentService = documentService;
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard(@RequestParam String phone) {
    return portalService.dashboard(phone);
  }

  @GetMapping("/profile")
  public Map<String, Object> profile(@RequestParam String phone) {
    return portalService.profile(phone);
  }

  @PutMapping("/profile")
  public Map<String, Object> updateProfile(
      @RequestParam String phone, @RequestBody Map<String, Object> body) {
    return portalService.updateProfile(phone, body);
  }

  @GetMapping("/queue-status")
  public Map<String, Object> queueStatus(
      @RequestParam(required = false) String phone,
      @RequestParam(required = false) String patientId) {
    return portalService.queueStatus(phone, patientId);
  }

  @GetMapping("/bookings")
  public Map<String, Object> bookings(
      @RequestParam(required = false) String phone,
      @RequestParam(required = false) String patientId) {
    return portalService.bookings(phone, patientId);
  }

  @GetMapping("/appointment-patients")
  public Map<String, Object> appointmentPatients(@RequestParam String phone) {
    return portalService.appointmentPatients(phone);
  }

  @GetMapping("/reports")
  public Map<String, Object> reports(
      @RequestParam String phone,
      @RequestParam(required = false) String patientName,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return portalService.reports(phone, patientName, from, to);
  }

  @PostMapping("/reports")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> uploadReport(@RequestBody Map<String, Object> body) {
    PatientReportEntity saved = portalService.uploadReport(body);
    return Map.of("message", "Report uploaded", "report", saved);
  }

  /**
   * Hospital-uploaded test documents for the selected patient (WHERE phone + patient_name).
   * Columns: patient name, document type, hospital name, document link.
   */
  @GetMapping("/documents")
  public Map<String, Object> documents(
      @RequestParam String phone, @RequestParam String patientName) {
    List<Map<String, Object>> rows = documentService.listForPatient(phone, patientName);
    return Map.of("count", rows.size(), "documents", rows);
  }

  /**
   * Download a document: reads {@code file_path} / {@code file_upload_*} from svc.documents and
   * fetches from Amazon S3, with local fallback.
   */
  @GetMapping("/documents/{id}/files/{slot}")
  public ResponseEntity<byte[]> downloadDocument(
      @PathVariable("id") String id,
      @PathVariable("slot") int slot,
      @RequestParam String phone) {
    byte[] body = documentService.resolveBytesForPatient(id, slot, phone);
    String filename = documentService.displayFileName(id, slot);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + filename.replace("\"", "") + "\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(body);
  }
}
