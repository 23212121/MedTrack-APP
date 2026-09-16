package com.medtrack.hrm.web;

import com.medtrack.hrm.domain.LeaveApplicationEntity;
import com.medtrack.hrm.domain.WfhRequestEntity;
import com.medtrack.hrm.service.HrmApproverService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hrm/approver")
public class HrmApproverController {
  private final HrmApproverService approver;

  public HrmApproverController(HrmApproverService approver) {
    this.approver = approver;
  }

  @GetMapping("/pending")
  public Map<String, Object> pending() {
    return approver.pending();
  }

  @PostMapping("/leave/{id}/decide")
  public LeaveApplicationEntity decideLeave(
      @PathVariable String id, @RequestBody Map<String, Object> body) {
    String status = body == null || body.get("status") == null ? "" : String.valueOf(body.get("status"));
    String remarks = body == null || body.get("remarks") == null ? null : String.valueOf(body.get("remarks"));
    return approver.decideLeave(id, status, remarks);
  }

  @PostMapping("/wfh/{id}/decide")
  public WfhRequestEntity decideWfh(
      @PathVariable String id, @RequestBody Map<String, Object> body) {
    String status = body == null || body.get("status") == null ? "" : String.valueOf(body.get("status"));
    return approver.decideWfh(id, status);
  }
}
