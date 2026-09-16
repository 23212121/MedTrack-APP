package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(schema = "svc", name = "hrm_attendance", uniqueConstraints = {
    @UniqueConstraint(name = "uk_hrm_att_emp_date", columnNames = {"employee_id", "attendance_date", "doctor_id"})
})
public class AttendanceEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "employee_pk", nullable = false, length = 64)
  private String employeePk;

  @Column(name = "employee_id", nullable = false, length = 40)
  private String employeeId;

  @Column(name = "employee_name", length = 160)
  private String employeeName;

  @Column(name = "attendance_date", nullable = false)
  private LocalDate attendanceDate;

  @Column(name = "in_time")
  private LocalTime inTime;

  @Column(name = "out_time")
  private LocalTime outTime;

  @Column(name = "total_hours", precision = 6, scale = 2)
  private BigDecimal totalHours;

  @Column(nullable = false, length = 20)
  private String status = "Present";

  @Column(name = "source", length = 40)
  private String source = "Manual";

  @Column(name = "correction_requested")
  private Boolean correctionRequested = false;

  @Column(name = "correction_reason", length = 400)
  private String correctionReason;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getEmployeePk() { return employeePk; }
  public void setEmployeePk(String employeePk) { this.employeePk = employeePk; }
  public String getEmployeeId() { return employeeId; }
  public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
  public String getEmployeeName() { return employeeName; }
  public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
  public LocalDate getAttendanceDate() { return attendanceDate; }
  public void setAttendanceDate(LocalDate attendanceDate) { this.attendanceDate = attendanceDate; }
  public LocalTime getInTime() { return inTime; }
  public void setInTime(LocalTime inTime) { this.inTime = inTime; }
  public LocalTime getOutTime() { return outTime; }
  public void setOutTime(LocalTime outTime) { this.outTime = outTime; }
  public BigDecimal getTotalHours() { return totalHours; }
  public void setTotalHours(BigDecimal totalHours) { this.totalHours = totalHours; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
  public Boolean getCorrectionRequested() { return correctionRequested; }
  public void setCorrectionRequested(Boolean correctionRequested) {
    this.correctionRequested = correctionRequested;
  }
  public String getCorrectionReason() { return correctionReason; }
  public void setCorrectionReason(String correctionReason) { this.correctionReason = correctionReason; }
}
