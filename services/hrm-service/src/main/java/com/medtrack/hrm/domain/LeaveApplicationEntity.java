package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Leave application row — user/doctor applies leave.
 * Mandatory: doctor_id, hospital_id, creation_date, creation_user, update_date, update_user.
 */
@Entity
@Table(
    schema = "svc",
    name = "leave_applications",
    indexes = {
      @Index(name = "idx_leave_app_hospital_doctor", columnList = "hospital_id, doctor_id"),
      @Index(name = "idx_leave_app_doctor_status", columnList = "doctor_id, status")
    })
public class LeaveApplicationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "doctor_id", nullable = false, length = 64)
  private String doctorId;

  @Column(name = "employee_id", nullable = false, length = 40)
  private String employeeId;

  @Column(name = "employee_name", length = 160)
  private String employeeName;

  @Column(name = "leave_type_id", nullable = false, length = 64)
  private String leaveTypeId;

  @Column(name = "leave_type", nullable = false, length = 80)
  private String leaveType;

  @Column(name = "from_date", nullable = false)
  private LocalDate fromDate;

  @Column(name = "to_date", nullable = false)
  private LocalDate toDate;

  @Column(nullable = false, precision = 6, scale = 2)
  private BigDecimal days = BigDecimal.ONE;

  @Column(length = 500)
  private String reason;

  @Column(nullable = false, length = 20)
  private String status = "Pending";

  @Column(name = "approver_remarks", length = 400)
  private String approverRemarks;

  @Column(name = "creation_date", nullable = false)
  private Instant creationDate;

  @Column(name = "creation_user", nullable = false, length = 100)
  private String creationUser;

  @Column(name = "update_date", nullable = false)
  private Instant updateDate;

  @Column(name = "update_user", nullable = false, length = 100)
  private String updateUser;

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    if (creationDate == null) creationDate = now;
    if (updateDate == null) updateDate = now;
    if (creationUser == null || creationUser.isBlank()) creationUser = "system";
    if (updateUser == null || updateUser.isBlank()) updateUser = creationUser;
    if (status == null || status.isBlank()) status = "Pending";
    if (days == null) days = BigDecimal.ONE;
  }

  @PreUpdate
  protected void onUpdate() {
    updateDate = Instant.now();
    if (updateUser == null || updateUser.isBlank()) updateUser = "system";
  }

  public void touchAudit(Long hospitalId, String doctorId, String user) {
    if (hospitalId != null) this.hospitalId = hospitalId;
    if (doctorId != null && !doctorId.isBlank()) this.doctorId = doctorId;
    if (user != null && !user.isBlank()) {
      if (this.creationUser == null || this.creationUser.isBlank()) {
        this.creationUser = user;
      }
      this.updateUser = user;
    }
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
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
  public BigDecimal getDays() { return days; }
  public void setDays(BigDecimal days) { this.days = days; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getApproverRemarks() { return approverRemarks; }
  public void setApproverRemarks(String approverRemarks) { this.approverRemarks = approverRemarks; }
  public Instant getCreationDate() { return creationDate; }
  public void setCreationDate(Instant creationDate) { this.creationDate = creationDate; }
  public String getCreationUser() { return creationUser; }
  public void setCreationUser(String creationUser) { this.creationUser = creationUser; }
  public Instant getUpdateDate() { return updateDate; }
  public void setUpdateDate(Instant updateDate) { this.updateDate = updateDate; }
  public String getUpdateUser() { return updateUser; }
  public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
}
