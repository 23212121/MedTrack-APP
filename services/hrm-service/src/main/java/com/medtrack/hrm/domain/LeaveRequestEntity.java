package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(schema = "svc", name = "hrm_leave_requests")
public class LeaveRequestEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "employee_pk", nullable = false, length = 64)
  private String employeePk;

  @Column(name = "employee_id", nullable = false, length = 40)
  private String employeeId;

  @Column(name = "employee_name", length = 160)
  private String employeeName;

  @Column(name = "leave_type_id", nullable = false, length = 64)
  private String leaveTypeId;

  @Column(name = "leave_type", length = 80)
  private String leaveType;

  @Column(name = "from_date", nullable = false)
  private LocalDate fromDate;

  @Column(name = "to_date", nullable = false)
  private LocalDate toDate;

  @Column(length = 500)
  private String reason;

  @Column(length = 500)
  private String attachment;

  @Column(nullable = false, length = 20)
  private String status = "Pending";

  @Column(name = "approver_remarks", length = 400)
  private String approverRemarks;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getEmployeePk() { return employeePk; }
  public void setEmployeePk(String employeePk) { this.employeePk = employeePk; }
  public String getEmployeeId() { return employeeId; }
  public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
  public String getEmployeeName() { return employeeName; }
  public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
  public String getLeaveTypeId() { return leaveTypeId; }
  public void setLeaveTypeId(String leaveTypeId) { this.leaveTypeId = leaveTypeId; }
  public String getLeaveType() { return leaveType; }
  public void setLeaveType(String leaveType) { this.leaveType = leaveType; }
  public LocalDate getFromDate() { return fromDate; }
  public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
  public LocalDate getToDate() { return toDate; }
  public void setToDate(LocalDate toDate) { this.toDate = toDate; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public String getAttachment() { return attachment; }
  public void setAttachment(String attachment) { this.attachment = attachment; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getApproverRemarks() { return approverRemarks; }
  public void setApproverRemarks(String approverRemarks) { this.approverRemarks = approverRemarks; }
}
