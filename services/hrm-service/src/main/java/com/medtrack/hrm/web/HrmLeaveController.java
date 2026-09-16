package com.medtrack.hrm.web;

import com.medtrack.hrm.domain.LeaveApplicationEntity;
import com.medtrack.hrm.service.HrmAppService;
import com.medtrack.hrm.service.HrmRightsService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hrm/leave")
public class HrmLeaveController {
  private final HrmAppService hrm;
  private final HrmRightsService rights;

  public HrmLeaveController(HrmAppService hrm, HrmRightsService rights) {
    this.hrm = hrm;
    this.rights = rights;
  }

  /** Leave summary for the doctor (balances + charts from DB). */
  @GetMapping("/summary")
  public Map<String, Object> summary(@RequestParam(required = false) Integer year) {
    return hrm.leaveSummary(year);
  }

  @GetMapping("/types")
  public Object types() {
    hrm.ensureLeaveCatalog();
    return hrm.listLeaveTypes();
  }

  @GetMapping("/balances")
  public Object balances() {
    return hrm.leaveSummary(null).get("balances");
  }

  @GetMapping("/requests")
  public Object requests(@RequestParam(required = false) String status) {
    return hrm.listLeaveApplications(status);
  }

  /** Apply leave — persists to svc.leave_applications with hospital_id + doctor_id + audit. */
  @PostMapping("/requests")
  public LeaveApplicationEntity requestLeave(@RequestBody Map<String, Object> body) {
    return hrm.requestLeaveForSelf(body);
  }

  @PostMapping("/requests/{id}/decide")
  public LeaveApplicationEntity decide(
      @PathVariable String id,
      @RequestParam String status,
      @RequestParam(required = false) String remarks) {
    rights.requireApprover();
    return hrm.decideLeaveApplication(id, status, remarks);
  }

  /**
   * Replace this doctor's leave entitlements. Body example:
   * [{"name":"Earned Leave","annualQuota":15},{"name":"Sick Leave","annualQuota":10},
   *  {"name":"Paternity Leave","annualQuota":15}]
   * Only these types get balance cards on the Leave page.
   */
  @PutMapping("/entitlements")
  public Object setEntitlements(@RequestBody java.util.List<java.util.Map<String, Object>> body) {
    return hrm.setDoctorLeaveEntitlements(body);
  }
}
