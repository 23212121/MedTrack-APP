package com.medtrack.booking.web;

import com.medtrack.booking.service.PaymentAppService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
  private final PaymentAppService service;

  public PaymentController(PaymentAppService service) {
    this.service = service;
  }

  @PostMapping("/create")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@RequestBody(required = false) Map<String, Object> body) {
    return service.create(body == null ? Map.of() : body);
  }

  @GetMapping("/{paymentId}")
  public Map<String, Object> get(@PathVariable String paymentId) {
    return service.get(paymentId);
  }

  @GetMapping("/appointment/{appointmentId}")
  public Map<String, Object> byAppointment(@PathVariable String appointmentId) {
    return service.getByAppointment(appointmentId);
  }

  @PostMapping("/webhook")
  public Map<String, Object> webhook(
      @RequestBody String rawBody,
      @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
    return service.handleWebhook(rawBody, signature);
  }

  @PostMapping("/{paymentId}/verify-with-gateway")
  public Map<String, Object> verify(@PathVariable String paymentId) {
    return service.verifyWithGateway(paymentId);
  }

  @PostMapping("/{paymentId}/cancel")
  public Map<String, Object> cancel(@PathVariable String paymentId) {
    return service.cancel(paymentId);
  }
}
