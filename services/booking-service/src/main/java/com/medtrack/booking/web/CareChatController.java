package com.medtrack.booking.web;

import com.medtrack.booking.service.CareChatService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/care-chats")
public class CareChatController {
  private final CareChatService service;

  public CareChatController(CareChatService service) {
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> patientThreads(@RequestParam String phone) {
    return service.patientThreads(phone);
  }

  @PostMapping("/unread-hints")
  public Map<String, Object> unreadHints(@RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> payload = body == null ? Map.of() : body;
    Object rawIds = payload.get("ids");
    List<String> ids = new java.util.ArrayList<>();
    if (rawIds instanceof Collection<?> collection) {
      for (Object id : collection) {
        if (id != null && !String.valueOf(id).isBlank()) ids.add(String.valueOf(id));
      }
    }
    return service.unreadHints(ids, payload.get("viewer") == null ? "" : String.valueOf(payload.get("viewer")));
  }

  @GetMapping("/attachments/{messageId}")
  public ResponseEntity<byte[]> attachment(
      @PathVariable String messageId,
      @RequestParam(required = false) String phone,
      @RequestParam(required = false) String hospitalId,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader,
      @RequestHeader(value = "X-Patient-Phone", required = false) String patientPhone) {
    CareChatService.FileDownload file =
        service.fileBytes(messageId, firstNonBlank(phone, patientPhone), firstNonBlank(hospitalId, hospitalHeader));
    String filename = file.filename() == null ? "attachment" : file.filename().replace("\"", "");
    boolean inline = file.contentType().getType().equals("image") || MediaType.APPLICATION_PDF.equals(file.contentType());
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            (inline ? "inline" : "attachment") + "; filename=\"" + filename + "\"")
        .contentType(file.contentType())
        .body(file.bytes());
  }

  @GetMapping("/{appointmentId}")
  public Map<String, Object> thread(
      @PathVariable String appointmentId, @RequestParam(required = false) String phone) {
    return service.thread(appointmentId, phone);
  }

  @PostMapping("/{appointmentId}")
  public Map<String, Object> post(
      @PathVariable String appointmentId,
      @RequestParam(required = false) String phone,
      @RequestBody(required = false) Map<String, Object> body) {
    return service.post(appointmentId, body == null ? Map.of() : body, phone);
  }

  @PostMapping(value = "/{appointmentId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> attach(
      @PathVariable String appointmentId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) String senderType,
      @RequestParam(required = false) String senderName,
      @RequestParam(required = false) String message,
      @RequestParam(required = false) String phone) {
    return service.attach(appointmentId, file, senderType, senderName, message, phone);
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }
}
