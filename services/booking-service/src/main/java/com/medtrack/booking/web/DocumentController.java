package com.medtrack.booking.web;

import com.medtrack.booking.domain.DocumentEntity;
import com.medtrack.booking.service.DocumentUploadService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
  private final DocumentUploadService service;

  public DocumentController(DocumentUploadService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> upload(
      @RequestParam("patientName") String patientName,
      @RequestParam("aadhaarNumber") String aadhaarNumber,
      @RequestParam("phoneNumber") String phoneNumber,
      @RequestParam(value = "files", required = false) MultipartFile[] files,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader,
      @RequestHeader(value = "X-User", required = false) String userHeader) {
    // Always bind upload to logged-in hospital session (ignore client hospitalId overrides)
    Long hospitalId = requireHospitalId(hospitalHeader);
    DocumentEntity saved =
        service.upload(hospitalId, patientName, aadhaarNumber, phoneNumber, files, userHeader);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("message", "Patient test documents uploaded");
    out.put("document", service.toMap(saved));
    return out;
  }

  /** List only documents for the session hospital: WHERE hospital_id = :sessionHospitalId */
  @GetMapping
  public Map<String, Object> list(
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader) {
    Long hospitalId = requireHospitalId(hospitalHeader);
    List<Map<String, Object>> rows =
        service.listForHospital(hospitalId).stream().map(service::toMap).toList();
    return Map.of("count", rows.size(), "documents", rows);
  }

  /**
   * Download a file for the session hospital. Reads {@code file_path} / {@code file_upload_N} from
   * {@code svc.documents} and fetches from Amazon S3 (local fallback).
   */
  @GetMapping("/{id}/files/{slot}")
  public ResponseEntity<byte[]> download(
      @PathVariable("id") String id,
      @PathVariable("slot") int slot,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader) {
    Long hospitalId = requireHospitalId(hospitalHeader);
    byte[] body = service.resolveBytesForHospital(id, slot, hospitalId);
    String filename = service.displayFileName(id, slot);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + filename.replace("\"", "") + "\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(body);
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
}
