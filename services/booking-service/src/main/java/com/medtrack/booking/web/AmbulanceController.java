package com.medtrack.booking.web;

import com.medtrack.booking.service.AmbulanceDispatchService;
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
@RequestMapping("/api")
public class AmbulanceController {
  private final AmbulanceDispatchService service;

  public AmbulanceController(AmbulanceDispatchService service) {
    this.service = service;
  }

  @GetMapping("/ambulances/pricing")
  public Map<String, Object> pricing(
      @RequestParam(value = "hospitalId", required = false) Long hospitalId,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader) {
    Long hid = hospitalId != null ? hospitalId : parseLong(hospitalHeader);
    return service.pricingConfig(hid);
  }

  @PostMapping("/ambulances/pricing")
  public Map<String, Object> savePricing(
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader) {
    Long hid = parseLong(hospitalHeader);
    if (hid == null) hid = parseLong(String.valueOf(body.get("hospitalId")));
    return service.updatePricing(hid, body == null ? Map.of() : body);
  }

  @GetMapping("/ambulance-requests/quote")
  public Map<String, Object> quote(
      @RequestParam("latitude") Double latitude,
      @RequestParam("longitude") Double longitude,
      @RequestParam(value = "hospitalId", required = false) Long hospitalId,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader) {
    Long hid = hospitalId != null ? hospitalId : parseLong(hospitalHeader);
    return service.quote(latitude, longitude, hid);
  }

  @GetMapping("/ambulances")
  public Map<String, Object> list(
      @RequestParam(value = "hospitalId", required = false) Long hospitalId,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader) {
    Long hid = hospitalId != null ? hospitalId : parseLong(hospitalHeader);
    return service.listAmbulances(hid);
  }

  @PostMapping("/ambulances")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> register(
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Hospital-Id", required = false) String hospitalHeader) {
    return service.registerAmbulance(parseLong(hospitalHeader), body == null ? Map.of() : body);
  }

  /** Flutter driver app: save FCM token and current GPS for this registered mobile. */
  @PostMapping("/ambulances/device")
  public Map<String, Object> device(@RequestBody Map<String, Object> body) {
    return service.registerDevice(body == null ? Map.of() : body);
  }

  /** Flutter driver app: live GPS heartbeat. */
  @PostMapping("/ambulances/location")
  public Map<String, Object> location(@RequestBody Map<String, Object> body) {
    return service.updateLocation(body == null ? Map.of() : body);
  }

  @PostMapping("/ambulance-requests")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> request(
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Patient-Phone", required = false) String phone,
      @RequestHeader(value = "X-Patient-Name", required = false) String name,
      @RequestHeader(value = "X-Patient-Id", required = false) String patientId) {
    return service.requestAmbulance(body == null ? Map.of() : body, phone, name, patientId);
  }

  @GetMapping("/ambulance-requests/mine")
  public Map<String, Object> mine(
      @RequestHeader(value = "X-Patient-Phone", required = false) String phone) {
    return service.myRequests(phone);
  }

  @PostMapping("/ambulance-requests/{id}/location")
  public Map<String, Object> patientLocation(
      @PathVariable("id") String id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "X-Patient-Phone", required = false) String phone) {
    return service.updatePatientLocation(id, phone, body == null ? Map.of() : body);
  }

  @PostMapping("/ambulance-requests/{id}/cancel")
  public Map<String, Object> cancel(
      @PathVariable("id") String id,
      @RequestHeader(value = "X-Patient-Phone", required = false) String phone) {
    return service.cancel(id, phone);
  }

  /** Flutter driver app polls this when a push is missed. */
  @GetMapping("/ambulance-driver/offers")
  public Map<String, Object> offers(@RequestParam("mobile") String mobile) {
    return service.driverOffers(mobile);
  }

  @PostMapping("/ambulance-requests/{id}/accept")
  public Map<String, Object> accept(
      @PathVariable("id") String id, @RequestParam("mobile") String mobile) {
    return service.accept(id, mobile);
  }

  @PostMapping("/ambulance-requests/{id}/reject")
  public Map<String, Object> reject(
      @PathVariable("id") String id, @RequestParam("mobile") String mobile) {
    return service.reject(id, mobile);
  }

  @PostMapping("/ambulance-requests/{id}/status")
  public Map<String, Object> status(
      @PathVariable("id") String id,
      @RequestParam("mobile") String mobile,
      @RequestParam("status") String status) {
    return service.markStatus(id, mobile, status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT));
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
