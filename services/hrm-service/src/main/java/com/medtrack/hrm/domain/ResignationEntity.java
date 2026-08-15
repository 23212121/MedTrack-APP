package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "hrm_resignations")
public class ResignationEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "employee_pk", nullable = false, length = 64)
  private String employeePk;

  @Column(name = "employee_id", nullable = false, length = 40)
  private String employeeId;

  @Column(name = "employee_name", length = 160)
  private String employeeName;

  @Column(name = "resignation_date", nullable = false)
  private LocalDate resignationDate;

  @Column(name = "last_working_day")
  private LocalDate lastWorkingDay;

  @Column(length = 500)
  private String reason;

  private Boolean assetReturn = false;
  private Boolean clearance = false;
  private Boolean finalSettlement = false;
  private Boolean experienceLetter = false;
  private Boolean relievingLetter = false;

  @Column(length = 40)
  private String status = "Submitted";

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getEmployeePk() { return employeePk; }
  public void setEmployeePk(String employeePk) { this.employeePk = employeePk; }
  public String getEmployeeId() { return employeeId; }
  public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
  public String getEmployeeName() { return employeeName; }
  public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
  public LocalDate getResignationDate() { return resignationDate; }
  public void setResignationDate(LocalDate resignationDate) { this.resignationDate = resignationDate; }
  public LocalDate getLastWorkingDay() { return lastWorkingDay; }
  public void setLastWorkingDay(LocalDate lastWorkingDay) { this.lastWorkingDay = lastWorkingDay; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public Boolean getAssetReturn() { return assetReturn; }
  public void setAssetReturn(Boolean assetReturn) { this.assetReturn = assetReturn; }
  public Boolean getClearance() { return clearance; }
  public void setClearance(Boolean clearance) { this.clearance = clearance; }
  public Boolean getFinalSettlement() { return finalSettlement; }
  public void setFinalSettlement(Boolean finalSettlement) { this.finalSettlement = finalSettlement; }
  public Boolean getExperienceLetter() { return experienceLetter; }
  public void setExperienceLetter(Boolean experienceLetter) { this.experienceLetter = experienceLetter; }
  public Boolean getRelievingLetter() { return relievingLetter; }
  public void setRelievingLetter(Boolean relievingLetter) { this.relievingLetter = relievingLetter; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
