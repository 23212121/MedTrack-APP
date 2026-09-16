package com.medtrack.hrm.service;

import com.medtrack.hrm.domain.EmployeeEntity;
import com.medtrack.hrm.domain.WfhRequestEntity;
import com.medtrack.hrm.repo.WfhRequestRepository;
import com.medtrack.hrm.web.AuditContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HrmWfhService {
  private final AuditContext audit;
  private final WfhRequestRepository wfhRepo;
  private final HrmAppService hrm;

  public HrmWfhService(
      AuditContext audit, WfhRequestRepository wfhRepo, @Lazy HrmAppService hrm) {
    this.audit = audit;
    this.wfhRepo = wfhRepo;
    this.hrm = hrm;
  }

  public List<WfhRequestEntity> list() {
    return wfhRepo.findByHospitalIdAndDoctorIdOrderByCreationDateDesc(
        audit.hospitalId(), audit.doctorId());
  }

  @Transactional
  public WfhRequestEntity request(Map<String, Object> body) {
    String fromRaw = str(body.get("fromDate"));
    String toRaw = str(body.get("toDate"));
    if (fromRaw == null || toRaw == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From and To dates are required");
    }
    LocalDate from = LocalDate.parse(fromRaw);
    LocalDate to = LocalDate.parse(toRaw);
    if (to.isBefore(from)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "To date must be on/after From date");
    }
    long days = ChronoUnit.DAYS.between(from, to) + 1;
    EmployeeEntity emp = hrm.ensureSelfEmployeeWithBalances();

    WfhRequestEntity req = new WfhRequestEntity();
    req.setEmployeeId(emp.getEmployeeId());
    req.setEmployeeName(emp.getFullName());
    req.setFromDate(from);
    req.setToDate(to);
    req.setDays(BigDecimal.valueOf(days));
    req.setNote(str(body.get("note")));
    req.setNotifyTo(str(body.get("notifyTo")));
    req.setStatus("Pending");
    req.touchAudit(audit.hospitalId(), audit.doctorId(), audit.user());
    return wfhRepo.save(req);
  }

  @Transactional
  public WfhRequestEntity decide(String id, String status) {
    WfhRequestEntity req =
        wfhRepo
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "WFH request not found"));
    if (audit.hospitalId() != null && !audit.hospitalId().equals(req.getHospitalId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "WFH is not in this hospital");
    }
    if (!"Pending".equalsIgnoreCase(req.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WFH is already " + req.getStatus());
    }
    if (!"Approved".equalsIgnoreCase(status) && !"Rejected".equalsIgnoreCase(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be Approved or Rejected");
    }
    req.setStatus("Approved".equalsIgnoreCase(status) ? "Approved" : "Rejected");
    req.touchAudit(audit.hospitalId(), audit.doctorId(), audit.user());
    return wfhRepo.save(req);
  }

  private static String str(Object o) {
    return o == null ? null : String.valueOf(o).trim();
  }
}
