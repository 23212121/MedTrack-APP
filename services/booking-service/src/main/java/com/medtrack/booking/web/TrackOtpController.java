package com.medtrack.booking.web;

import com.medtrack.booking.service.TrackOtpService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/track")
public class TrackOtpController {
  private final TrackOtpService service;

  public TrackOtpController(TrackOtpService service) {
    this.service = service;
  }

  @GetMapping("/otp-flag")
  public Map<String, Object> flag() {
    return service.flagStatus();
  }

  @PostMapping("/otp/send")
  public Map<String, Object> send(@RequestBody(required = false) Map<String, Object> body) {
    return service.send(str(body, "phone"));
  }

  @PostMapping("/otp/verify")
  public Map<String, Object> verify(@RequestBody(required = false) Map<String, Object> body) {
    return service.verify(str(body, "phone"), str(body, "otp"));
  }

  private static String str(Map<String, Object> body, String key) {
    if (body == null || body.get(key) == null) return "";
    return String.valueOf(body.get(key));
  }
}
