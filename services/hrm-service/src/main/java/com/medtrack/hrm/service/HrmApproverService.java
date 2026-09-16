package com.medtrack.hrm.service;

import com.medtrack.hrm.domain.LeaveApplicationEntity;
import com.medtrack.hrm.domain.WfhRequestEntity;
import com.medtrack.hrm.repo.LeaveApplicationRepository;
import com.medtrack.hrm.repo.WfhRequestRepository;
import com.medtrack.hrm.web.AuditContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HrmApproverService {
  private final AuditContext audit;
  private final HrmRightsService rights;
  private final HrmAppService hrm;
  private final HrmWfhService wfh;
  private final LeaveApplicationRepository leaveApps;
  private final WfhRequestRepository wfhRepo;

  public HrmApproverService(
      AuditContext audit,
      HrmRightsService rights,
      HrmAppService hrm,
      HrmWfhService wfh,
      LeaveApplicationRepository leaveApps,
      WfhRequestRepository wfhRepo) {
    this.audit = audit;
    this.rights = rights;
    this.hrm = hrm;
    this.wfh = wfh;
    this.leaveApps = leaveApps;
    this.wfhRepo = wfhRepo;
  }

  public Map<String, Object> pending() {
    rights.requireApprover();
    Long hospitalId = audit.hospitalId();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put(
        "leave",
        leaveApps.findByHospitalIdAndStatusOrderByCreationDateDesc(hospitalId, "Pending"));
    out.put(
        "wfh",
        wfhRepo.findByHospitalIdAndStatusOrderByCreationDateDesc(hospitalId, "Pending"));
    return out;
  }

  @Transactional
  public LeaveApplicationEntity decideLeave(String id, String status, String remarks) {
    rights.requireApprover();
    return hrm.decideLeaveApplication(id, status, remarks);
  }

  @Transactional
  public WfhRequestEntity decideWfh(String id, String status) {
    rights.requireApprover();
    return wfh.decide(id, status);
  }
}
