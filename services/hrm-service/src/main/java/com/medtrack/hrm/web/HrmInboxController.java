package com.medtrack.hrm.web;

import com.medtrack.hrm.domain.HrmInboxEntity;
import com.medtrack.hrm.service.HrmInboxService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hrm/inbox")
public class HrmInboxController {
  private final HrmInboxService inbox;

  public HrmInboxController(HrmInboxService inbox) {
    this.inbox = inbox;
  }

  /** Take-action / archive summary with category counts. */
  @GetMapping
  public Map<String, Object> list(
      @RequestParam(defaultValue = "action") String view,
      @RequestParam(required = false) String category) {
    return inbox.list(view, category);
  }

  @GetMapping("/{id}")
  public HrmInboxEntity get(@PathVariable String id) {
    return inbox.get(id);
  }

  /** Release a document into the employee's inbox (performance / letter / salary increment). */
  @PostMapping
  public HrmInboxEntity release(@RequestBody Map<String, Object> body) {
    return inbox.releaseDocument(body);
  }

  @PostMapping("/{id}/acknowledge")
  public HrmInboxEntity acknowledge(@PathVariable String id) {
    return inbox.acknowledge(id);
  }

  @PostMapping("/{id}/download")
  public HrmInboxEntity download(@PathVariable String id) {
    return inbox.markDownloaded(id);
  }

  @PostMapping("/{id}/archive")
  public HrmInboxEntity archive(@PathVariable String id) {
    return inbox.archive(id);
  }

  @GetMapping("/seed")
  public List<HrmInboxEntity> seed() {
    return inbox.ensureDemoItems();
  }
}
