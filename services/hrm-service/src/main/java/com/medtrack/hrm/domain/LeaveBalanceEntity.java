package com.medtrack.hrm.domain;

import jakarta.persistence.*;

@Entity
@Table(schema = "svc", name = "hrm_leave_balances", uniqueConstraints = {
    @UniqueConstraint(name = "uk_hrm_leave_bal", columnNames = {"employee_id", "leave_type_id", "doctor_id"})
})
public class LeaveBalanceEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "employee_pk", nullable = false, length = 64)
  private String employeePk;

  @Column(name = "employee_id", nullable = false, length = 40)
  private String employeeId;

  @Column(name = "leave_type_id", nullable = false, length = 64)
  private String leaveTypeId;

  @Column(name = "leave_type", length = 80)
  private String leaveType;

  @Column(name = "total_leave")
  private Integer totalLeave = 0;

  @Column(name = "used_leave")
  private Integer usedLeave = 0;

  @Column(name = "remaining_leave")
  private Integer remainingLeave = 0;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getEmployeePk() { return employeePk; }
  public void setEmployeePk(String employeePk) { this.employeePk = employeePk; }
  public String getEmployeeId() { return employeeId; }
  public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
  public String getLeaveTypeId() { return leaveTypeId; }
  public void setLeaveTypeId(String leaveTypeId) { this.leaveTypeId = leaveTypeId; }
  public String getLeaveType() { return leaveType; }
  public void setLeaveType(String leaveType) { this.leaveType = leaveType; }
  public Integer getTotalLeave() { return totalLeave; }
  public void setTotalLeave(Integer totalLeave) { this.totalLeave = totalLeave; }
  public Integer getUsedLeave() { return usedLeave; }
  public void setUsedLeave(Integer usedLeave) { this.usedLeave = usedLeave; }
  public Integer getRemainingLeave() { return remainingLeave; }
  public void setRemainingLeave(Integer remainingLeave) { this.remainingLeave = remainingLeave; }
}
