package com.medtrack.notify.web;

import com.medtrack.common.dto.NotifyRequest;
import com.medtrack.notify.service.NotificationAppService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationAppService service;

  public NotificationController(NotificationAppService service) {
    this.service = service;
  }

  @PostMapping("/send")
  public Map<String, Object> send(@RequestBody NotifyRequest req) {
    return service.send(req);
  }

  @GetMapping
  public Map<String, Object> recent() {
    return Map.of("notifications", service.recent());
  }

  @GetMapping("/visit/{visitId}")
  public Map<String, Object> byVisit(@PathVariable String visitId) {
    return Map.of("notifications", service.byVisit(visitId));
  }
}
