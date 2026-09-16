package com.medtrack.app.web;

import com.medtrack.app.status.SystemStatusService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  private final SystemStatusService statusService;

  public HealthController(SystemStatusService statusService) {
    this.statusService = statusService;
  }

  /** Simple liveness (kept for existing clients). Prefer /actuator/health. */
  @GetMapping("/api/health")
  public Map<String, String> health() {
    return Map.of("status", "UP", "service", "medtrack-app", "actuator", "/actuator/health");
  }

  /**
   * Aggregated service status using Spring Boot Actuator + optional remote probes.
   *
   * <p>Also available: {@code GET /actuator/health}, {@code GET /actuator/info}
   */
  @GetMapping("/api/system/status")
  public Map<String, Object> systemStatus() {
    return statusService.aggregate();
  }
}
