package com.medtrack.booking.web;

import com.medtrack.booking.domain.StatusEntity;
import com.medtrack.booking.repo.StatusRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/statuses")
public class StatusController {
  private final StatusRepository repo;

  public StatusController(StatusRepository repo) {
    this.repo = repo;
  }

  @GetMapping
  public Map<String, Object> list() {
    List<Map<String, Object>> statuses =
        repo.findAllByOrderByStatusIdAsc().stream().map(this::toMap).toList();
    return Map.of("count", statuses.size(), "statuses", statuses);
  }

  @GetMapping("/{statusId}")
  public Map<String, Object> get(@PathVariable Integer statusId) {
    StatusEntity row =
        repo.findById(statusId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown status: " + statusId));
    return Map.of("status", toMap(row));
  }

  private Map<String, Object> toMap(StatusEntity s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("statusId", s.getStatusId());
    m.put("statusCode", s.getStatusCode());
    m.put("statusName", s.getStatusName());
    return m;
  }
}
