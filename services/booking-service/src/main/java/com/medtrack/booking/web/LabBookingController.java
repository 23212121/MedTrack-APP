package com.medtrack.booking.web;

import com.medtrack.booking.service.LabBookingAppService;
import com.medtrack.booking.service.LabBookingAppService.Actor;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lab-bookings")
public class LabBookingController {
  private final LabBookingAppService service;

  public LabBookingController(LabBookingAppService service) {
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(value = "status", required = false) String status,
      @RequestHeader Map<String, String> headers) {
    return service.list(actor(headers), status);
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

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(
      @RequestBody Map<String, Object> body, @RequestHeader Map<String, String> headers) {
    return service.create(actor(headers), body == null ? Map.of() : body);
  }

  @PostMapping("/{id}/accept")
  public Map<String, Object> accept(
      @PathVariable("id") String id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader Map<String, String> headers) {
    return service.accept(actor(headers), body == null ? Map.of() : body, id);
  }

  @PostMapping("/{id}/reached-home")
  public Map<String, Object> reachedHome(
      @PathVariable("id") String id, @RequestHeader Map<String, String> headers) {
    return service.reachedHome(actor(headers), id);
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
    return service.cancel(actor(headers), body == null ? Map.of() : body, id);
  }

  @GetMapping("/{id}/chats")
  public Map<String, Object> chats(
      @PathVariable("id") String id, @RequestHeader Map<String, String> headers) {
    return service.listChats(actor(headers), id);
  }

  @PostMapping("/{id}/chats")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> postChat(
      @PathVariable("id") String id,
      @RequestBody Map<String, Object> body,
      @RequestHeader Map<String, String> headers) {
    return service.postChat(actor(headers), id, body == null ? Map.of() : body);
  }

  private Actor actor(Map<String, String> headers) {
    String loginType = header(headers, "X-Login-Type");
    Long hospitalId = toLong(header(headers, "X-Hospital-Id"));
    String labId = header(headers, "X-Lab-Id");
    String phone = header(headers, "X-Patient-Phone");
    String patientId = header(headers, "X-Patient-Id");
    String patientName = header(headers, "X-Patient-Name");
    String user = header(headers, "X-User");
    if (loginType == null || loginType.isBlank()) {
      if (labId != null && !labId.isBlank()) loginType = "LAB";
      else if (phone != null && !phone.isBlank()) loginType = "PATIENT";
      else loginType = "HOSPITAL";
    }
    if ("LAB".equalsIgnoreCase(loginType) && (labId == null || labId.isBlank()) && user != null) {
      labId = user;
    }
    return new Actor(loginType, hospitalId, labId, phone, patientId, patientName, user);
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
}
