package com.medtrack.booking.web;

import com.medtrack.booking.service.EmergencyAppService;
import com.medtrack.booking.service.EmergencyAppService.Actor;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/emergency")
public class EmergencyController {
  private final EmergencyAppService service;

  public EmergencyController(EmergencyAppService service) {
    this.service = service;
  }

  @GetMapping("/context")
  public Map<String, Object> context(@RequestHeader HttpHeaders headers) {
    return service.context(
        header(headers, "X-Login-Type"),
        toLong(header(headers, "X-Hospital-Id")),
        header(headers, "X-Doctor-Id"),
        header(headers, "X-Medical-Store-Id"),
        header(headers, "X-Patient-Phone"),
        header(headers, "X-Patient-Id"),
        header(headers, "X-User"));
  }

  @GetMapping("/hospitals")
  public Map<String, Object> hospitals(
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String q) {
    return service.searchHospitals(state, city, q);
  }

  @GetMapping("/hospitals/{hospitalId}")
  public Map<String, Object> hospital(@PathVariable Long hospitalId) {
    return service.hospitalDetail(hospitalId);
  }

  @PostMapping("/beds/{bedId}/book")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> book(
      @PathVariable String bedId,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader HttpHeaders headers) {
    return service.book(bedId, body == null ? Map.of() : body, actor(headers));
  }

  @GetMapping("/bookings/{bookingId}")
  public Map<String, Object> booking(@PathVariable String bookingId) {
    return service.getBooking(bookingId);
  }

  @PostMapping("/bookings/{bookingId}/pay")
  public Map<String, Object> pay(@PathVariable String bookingId) {
    return service.createPayment(bookingId);
  }

  @PostMapping("/bookings/{bookingId}/slip")
  public Map<String, Object> slip(
      @PathVariable String bookingId,
      @RequestPart("file") MultipartFile file,
      @RequestHeader HttpHeaders headers) {
    return service.attachSlip(bookingId, file, actor(headers));
  }

  private static Actor actor(HttpHeaders headers) {
    return new Actor(
        header(headers, "X-Login-Type"),
        header(headers, "X-User"),
        header(headers, "X-Patient-Name"),
        header(headers, "X-Patient-Phone"),
        header(headers, "X-Patient-Id"));
  }

  private static String header(HttpHeaders headers, String name) {
    String v = headers.getFirst(name);
    return v == null ? "" : v.trim();
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
