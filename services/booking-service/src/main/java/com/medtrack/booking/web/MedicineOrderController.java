package com.medtrack.booking.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.booking.service.MedicineOrderAppService;
import com.medtrack.booking.service.MedicineOrderAppService.Actor;
import com.medtrack.booking.service.MedicineOrderAppService.FilePayload;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/medicine-orders")
public class MedicineOrderController {
  private final MedicineOrderAppService service;
  private final ObjectMapper mapper;

  public MedicineOrderController(MedicineOrderAppService service, ObjectMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String amountStatus,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String fulfillment,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestHeader Map<String, String> headers) {
    return service.list(actor(headers), status, amountStatus, q, fulfillment, from, to);
  }

  @GetMapping("/counts")
  public Map<String, Object> counts(@RequestHeader Map<String, String> headers) {
    return service.counts(actor(headers));
  }

  @GetMapping("/notifications")
  public Map<String, Object> notifications(@RequestHeader Map<String, String> headers) {
    return service.notifications(actor(headers));
  }

  @PostMapping("/notifications/{id}/read")
  public Map<String, Object> markRead(
      @PathVariable("id") String id, @RequestHeader Map<String, String> headers) {
    return service.markRead(actor(headers), id);
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(
      @PathVariable("id") String id, @RequestHeader Map<String, String> headers) {
    return service.get(actor(headers), id);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(
      @RequestParam(value = "hospitalId", required = false) String hospitalId,
      @RequestParam(value = "storeId", required = false) String storeId,
      @RequestParam(value = "patientName", required = false) String patientName,
      @RequestParam(value = "patientPhone", required = false) String patientPhone,
      @RequestParam(value = "patientId", required = false) String patientId,
      @RequestParam(value = "doctorId", required = false) String doctorId,
      @RequestParam(value = "doctorName", required = false) String doctorName,
      @RequestParam(value = "fulfillment", required = false) String fulfillment,
      @RequestParam(value = "deliveryAddress", required = false) String deliveryAddress,
      @RequestParam(value = "notes", required = false) String notes,
      @RequestParam(value = "files", required = false) MultipartFile[] files,
      @RequestHeader Map<String, String> headers) {
    Map<String, Object> body = new HashMap<>();
    body.put("hospitalId", hospitalId);
    body.put("storeId", storeId);
    body.put("patientName", patientName);
    body.put("patientPhone", patientPhone);
    body.put("patientId", patientId);
    body.put("doctorId", doctorId);
    body.put("doctorName", doctorName);
    body.put("fulfillment", fulfillment);
    body.put("deliveryAddress", deliveryAddress);
    body.put("notes", notes);
    return service.create(actor(headers), body, files);
  }

  @PostMapping("/{id}/accept")
  public Map<String, Object> accept(
      @PathVariable("id") String id, @RequestHeader Map<String, String> headers) {
    return service.accept(actor(headers), id);
  }

  @PostMapping("/{id}/reject")
  public Map<String, Object> reject(
      @PathVariable("id") String id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader Map<String, String> headers) {
    return service.reject(actor(headers), id, body == null ? Map.of() : body);
  }

  @PostMapping("/{id}/pending")
  public Map<String, Object> pending(
      @PathVariable("id") String id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader Map<String, String> headers) {
    return service.markPending(actor(headers), id, body == null ? Map.of() : body);
  }

  @PutMapping("/{id}/quote")
  public Map<String, Object> quote(
      @PathVariable("id") String id,
      @RequestParam(value = "send", defaultValue = "false") boolean send,
      @RequestBody Map<String, Object> body,
      @RequestHeader Map<String, String> headers) {
    return service.saveQuote(actor(headers), id, body, send);
  }

  @PostMapping("/{id}/amount/accept")
  public Map<String, Object> acceptAmount(
      @PathVariable("id") String id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader Map<String, String> headers) {
    return service.acceptAmount(actor(headers), id, body == null ? Map.of() : body);
  }

  @PostMapping("/{id}/payment/razorpay/order")
  public Map<String, Object> razorpayOrder(
      @PathVariable("id") String id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader Map<String, String> headers) {
    return service.createRazorpayOrder(actor(headers), id, body == null ? Map.of() : body);
  }

  @PostMapping("/{id}/payment/razorpay/verify")
  public Map<String, Object> razorpayVerify(
      @PathVariable("id") String id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader Map<String, String> headers) {
    return service.verifyRazorpayPayment(actor(headers), id, body == null ? Map.of() : body);
  }

  @PostMapping(value = "/{id}/payment/screenshot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> paymentScreenshot(
      @PathVariable("id") String id,
      @RequestParam("files") MultipartFile[] files,
      @RequestParam(value = "payload", required = false) String payload,
      @RequestHeader Map<String, String> headers) {
    Map<String, Object> body = Map.of();
    if (payload != null && !payload.isBlank()) {
      try {
        body = mapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
      } catch (Exception ex) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment payload");
      }
    }
    return service.payWithScreenshot(actor(headers), id, body, files);
  }

  @PostMapping("/{id}/amount/reject")
  public Map<String, Object> rejectAmount(
      @PathVariable("id") String id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader Map<String, String> headers) {
    return service.rejectAmount(actor(headers), id, body == null ? Map.of() : body);
  }

  @PostMapping("/{id}/ready")
  public Map<String, Object> ready(
      @PathVariable("id") String id, @RequestHeader Map<String, String> headers) {
    return service.markReady(actor(headers), id);
  }

  @PostMapping("/{id}/complete")
  public Map<String, Object> complete(
      @PathVariable("id") String id, @RequestHeader Map<String, String> headers) {
    return service.complete(actor(headers), id);
  }

  @PostMapping("/{id}/cancel")
  public Map<String, Object> cancel(
      @PathVariable("id") String id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader Map<String, String> headers) {
    return service.cancel(actor(headers), id, body == null ? Map.of() : body);
  }

  @PostMapping("/{id}/release")
  public Map<String, Object> release(
      @PathVariable("id") String id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader Map<String, String> headers) {
    return service.release(actor(headers), id, body == null ? Map.of() : body);
  }

  @PostMapping("/{id}/prescription-unclear")
  public Map<String, Object> unclear(
      @PathVariable("id") String id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader Map<String, String> headers) {
    return service.markUnclear(actor(headers), id, body == null ? Map.of() : body);
  }

  @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> uploadDocs(
      @PathVariable("id") String id,
      @RequestParam("files") MultipartFile[] files,
      @RequestHeader Map<String, String> headers) {
    return service.uploadDocuments(actor(headers), id, files);
  }

  @GetMapping("/{id}/documents/{docId}")
  public ResponseEntity<byte[]> document(
      @PathVariable("id") String id,
      @PathVariable("docId") String docId,
      @RequestHeader Map<String, String> headers) {
    FilePayload file = service.documentFile(actor(headers), id, docId);
    HttpHeaders http = new HttpHeaders();
    MediaType type = MediaType.APPLICATION_OCTET_STREAM;
    if (file.contentType() != null && !file.contentType().isBlank()) {
      try {
        type = MediaType.parseMediaType(file.contentType());
      } catch (Exception ignored) {
        type = MediaType.APPLICATION_OCTET_STREAM;
      }
    }
    http.setContentType(type);
    http.setContentDisposition(ContentDisposition.inline().filename(file.fileName()).build());
    return new ResponseEntity<>(file.bytes(), http, HttpStatus.OK);
  }

  private Actor actor(Map<String, String> headers) {
    String loginType = header(headers, "X-Login-Type");
    Long hospitalId = toLong(header(headers, "X-Hospital-Id"));
    String storeId = header(headers, "X-Medical-Store-Id");
    String doctorId = header(headers, "X-Doctor-Id");
    String phone = header(headers, "X-Patient-Phone");
    String patientId = header(headers, "X-Patient-Id");
    String patientName = header(headers, "X-Patient-Name");
    String user = header(headers, "X-User");
    if (loginType == null || loginType.isBlank()) {
      if (storeId != null && !storeId.isBlank()) loginType = "MEDICAL";
      else if (phone != null && !phone.isBlank() && hospitalId == null) loginType = "PATIENT";
      else if (doctorId != null && !doctorId.isBlank() && user != null) loginType = "USER";
      else loginType = "HOSPITAL";
    }
    if ("MEDICAL".equalsIgnoreCase(loginType) && (storeId == null || storeId.isBlank()) && user != null) {
      storeId = user;
    }
    if ("MEDICAL".equalsIgnoreCase(loginType)) {
      doctorId = null;
    }
    return new Actor(loginType, hospitalId, storeId, doctorId, phone, patientId, patientName, user);
  }

  private static String header(Map<String, String> headers, String name) {
    if (headers == null) return null;
    String direct = headers.get(name);
    if (direct != null) return direct;
    for (Map.Entry<String, String> e : headers.entrySet()) {
      if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
    }
    return null;
  }

  private static Long toLong(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  @SuppressWarnings("unused")
  private Map<String, Object> parseJson(String raw) {
    if (raw == null || raw.isBlank()) return Map.of();
    try {
      return mapper.readValue(raw, new TypeReference<>() {});
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON");
    }
  }
}
