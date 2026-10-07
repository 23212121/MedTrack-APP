package com.medtrack.hrm.service;

import com.medtrack.hrm.domain.AttendanceEntity;
import com.medtrack.hrm.domain.EmployeeEntity;
import com.medtrack.hrm.domain.LeaveApplicationEntity;
import com.medtrack.hrm.domain.LeaveBalanceEntity;
import com.medtrack.hrm.domain.WfhRequestEntity;
import com.medtrack.hrm.repo.AttendanceRepository;
import com.medtrack.hrm.repo.EmployeeRepository;
import com.medtrack.hrm.repo.LeaveApplicationRepository;
import com.medtrack.hrm.repo.LeaveBalanceRepository;
import com.medtrack.hrm.repo.WfhRequestRepository;
import com.medtrack.hrm.web.AuditContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
  private final EmployeeRepository employees;
  private final AttendanceRepository attendance;
  private final LeaveBalanceRepository leaveBalances;
  private final HrmNotificationService mail;

  public HrmApproverService(
      AuditContext audit,
      HrmRightsService rights,
      HrmAppService hrm,
      HrmWfhService wfh,
      LeaveApplicationRepository leaveApps,
      WfhRequestRepository wfhRepo,
      EmployeeRepository employees,
      AttendanceRepository attendance,
      LeaveBalanceRepository leaveBalances,
      HrmNotificationService mail) {
    this.audit = audit;
    this.rights = rights;
    this.hrm = hrm;
    this.wfh = wfh;
    this.leaveApps = leaveApps;
    this.wfhRepo = wfhRepo;
    this.employees = employees;
    this.attendance = attendance;
    this.leaveBalances = leaveBalances;
    this.mail = mail;
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

  public Map<String, Object> employees() {
    rights.requireApprover();
    Long hospitalId = audit.hospitalId();
    Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
    for (EmployeeEntity e : employees.findAll()) {
      putEmployee(byId, e.getEmployeeId(), e.getFullName(), e.getDepartment(), e.getDesignation());
    }
    for (LeaveApplicationEntity row : leaveApps.findByHospitalIdOrderByCreationDateDesc(hospitalId)) {
      putEmployee(byId, row.getEmployeeId(), row.getEmployeeName(), null, null);
    }
    for (WfhRequestEntity row :
        wfhRepo.findByHospitalIdAndStatusOrderByCreationDateDesc(hospitalId, "Pending")) {
      putEmployee(byId, row.getEmployeeId(), row.getEmployeeName(), null, null);
    }
    List<Map<String, Object>> list = new ArrayList<>(byId.values());
    list.sort(
        Comparator.comparing(
            m -> String.valueOf(m.getOrDefault("name", "")).toLowerCase()));
    return Map.of("count", list.size(), "employees", list);
  }

  public Map<String, Object> employeeFile(String employeeId) {
    rights.requireApprover();
    if (employeeId == null || employeeId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "employeeId is required");
    }
    String id = employeeId.trim();
    Long hospitalId = audit.hospitalId();
    EmployeeEntity emp = employees.findByEmployeeId(id).orElse(null);

    List<LeaveApplicationEntity> leave =
        leaveApps.findByHospitalIdAndEmployeeIdOrderByCreationDateDesc(hospitalId, id);
    List<WfhRequestEntity> wfhRows =
        wfhRepo.findByHospitalIdAndEmployeeIdOrderByCreationDateDesc(hospitalId, id);
    List<AttendanceEntity> logs = attendance.findByEmployeeIdOrderByAttendanceDateDesc(id);
    List<LeaveBalanceEntity> bals = leaveBalances.findByEmployeeId(id);

    String name =
        emp != null
            ? emp.getFullName()
            : leave.stream()
                .map(LeaveApplicationEntity::getEmployeeName)
                .filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElse(id);

    Map<String, Object> employee = new LinkedHashMap<>();
    employee.put("employeeId", id);
    employee.put("name", name);
    if (emp != null) {
      employee.put("department", emp.getDepartment());
      employee.put("designation", emp.getDesignation());
      employee.put("employeeType", emp.getEmployeeType());
      employee.put("doctorLinkId", emp.getDoctorLinkId());
    }

    List<Map<String, Object>> balanceRows = new ArrayList<>();
    for (LeaveBalanceEntity b : bals) {
      int total = b.getTotalLeave() == null ? 0 : b.getTotalLeave();
      int used = b.getUsedLeave() == null ? 0 : b.getUsedLeave();
      int available =
          b.getRemainingLeave() != null ? b.getRemainingLeave() : Math.max(0, total - used);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("leaveType", b.getLeaveType());
      row.put("total", total);
      row.put("used", used);
      row.put("available", available);
      balanceRows.add(row);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("employee", employee);
    out.put("attendance", logs);
    out.put("leave", leave);
    out.put("wfh", wfhRows);
    out.put("balances", balanceRows);
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

  @Transactional
  public AttendanceEntity decideAttendance(String id, String status) {
    rights.requireApprover();
    AttendanceEntity row =
        attendance
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance not found"));
    if (status == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
    }
    boolean approve =
        "Approved".equalsIgnoreCase(status.trim()) || "Approve".equalsIgnoreCase(status.trim());
    boolean dismiss =
        "Dismissed".equalsIgnoreCase(status.trim())
            || "Dismiss".equalsIgnoreCase(status.trim())
            || "Rejected".equalsIgnoreCase(status.trim());
    if (!approve && !dismiss) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be Approved or Dismissed");
    }
    row.setStatus(approve ? "Approved" : "Dismissed");
    row.setCorrectionRequested(false);
    row.touchAudit(audit.doctorId(), audit.user());
    AttendanceEntity saved = attendance.save(row);
    mail.notify(
        saved.getEmployeeId(),
        null,
        "Attendance " + saved.getStatus(),
        "Your attendance correction for "
            + saved.getAttendanceDate()
            + " is "
            + saved.getStatus()
            + ".");
    return saved;
  }

  private static void putEmployee(
      Map<String, Map<String, Object>> byId,
      String employeeId,
      String name,
      String department,
      String designation) {
    if (employeeId == null || employeeId.isBlank()) return;
    Map<String, Object> row = byId.computeIfAbsent(employeeId, k -> new LinkedHashMap<>());
    row.put("employeeId", employeeId);
    if (name != null && !name.isBlank() && isBlank(row.get("name"))) {
      row.put("name", name.trim());
    }
    if (department != null && !department.isBlank() && isBlank(row.get("department"))) {
      row.put("department", department);
    }
    if (designation != null && !designation.isBlank() && isBlank(row.get("designation"))) {
      row.put("designation", designation);
    }
    if (isBlank(row.get("name"))) {
      row.put("name", employeeId);
    }
  }

  private static boolean isBlank(Object v) {
    return v == null || String.valueOf(v).isBlank();
  }
}
