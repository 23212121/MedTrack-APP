package com.medtrack.visit.web;

import com.medtrack.visit.service.DoctorQueueService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queue")
public class PatientQueueController {
  private final DoctorQueueService queueService;

  public PatientQueueController(DoctorQueueService queueService) {
    this.queueService = queueService;
  }

  /** Board view: waiting count + current consulting patient (no personal token). */
  @GetMapping("/board")
  public Map<String, Object> board(
      @RequestParam Long hospitalId, @RequestParam String doctorId) {
    return queueService.board(hospitalId, doctorId);
  }

  /** Full status including optional personal token ETA. */
  @GetMapping("/status")
  public Map<String, Object> status(
      @RequestParam Long hospitalId,
      @RequestParam String doctorId,
      @RequestParam(required = false) Integer tokenNo) {
    return queueService.status(hospitalId, doctorId, tokenNo);
  }
}
