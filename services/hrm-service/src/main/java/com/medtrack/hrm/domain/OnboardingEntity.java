package com.medtrack.hrm.domain;

import jakarta.persistence.*;

@Entity
@Table(schema = "svc", name = "hrm_onboarding", uniqueConstraints = {
    @UniqueConstraint(name = "uk_hrm_onboard_emp", columnNames = {"employee_id", "doctor_id"})
})
public class OnboardingEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "employee_pk", nullable = false, length = 64)
  private String employeePk;

  @Column(name = "employee_id", nullable = false, length = 40)
  private String employeeId;

  @Column(name = "employee_name", length = 160)
  private String employeeName;

  private Boolean documentVerification = false;
  private Boolean idCardGenerated = false;
  private Boolean emailCreated = false;
  private Boolean departmentAssigned = false;
  private Boolean shiftAssigned = false;
  private Boolean salaryAssigned = false;

  @Column(length = 40)
  private String status = "In Progress";

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getEmployeePk() { return employeePk; }
  public void setEmployeePk(String employeePk) { this.employeePk = employeePk; }
  public String getEmployeeId() { return employeeId; }
  public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
  public String getEmployeeName() { return employeeName; }
  public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
  public Boolean getDocumentVerification() { return documentVerification; }
  public void setDocumentVerification(Boolean documentVerification) { this.documentVerification = documentVerification; }
  public Boolean getIdCardGenerated() { return idCardGenerated; }
  public void setIdCardGenerated(Boolean idCardGenerated) { this.idCardGenerated = idCardGenerated; }
  public Boolean getEmailCreated() { return emailCreated; }
  public void setEmailCreated(Boolean emailCreated) { this.emailCreated = emailCreated; }
  public Boolean getDepartmentAssigned() { return departmentAssigned; }
  public void setDepartmentAssigned(Boolean departmentAssigned) { this.departmentAssigned = departmentAssigned; }
  public Boolean getShiftAssigned() { return shiftAssigned; }
  public void setShiftAssigned(Boolean shiftAssigned) { this.shiftAssigned = shiftAssigned; }
  public Boolean getSalaryAssigned() { return salaryAssigned; }
  public void setSalaryAssigned(Boolean salaryAssigned) { this.salaryAssigned = salaryAssigned; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
