package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(schema = "svc", name = "hrm_salary_structures", uniqueConstraints = {
    @UniqueConstraint(name = "uk_hrm_salary_emp", columnNames = {"employee_id", "doctor_id"})
})
public class SalaryStructureEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "employee_pk", nullable = false, length = 64)
  private String employeePk;

  @Column(name = "employee_id", nullable = false, length = 40)
  private String employeeId;

  @Column(name = "employee_name", length = 160)
  private String employeeName;

  @Column(name = "basic_salary", precision = 12, scale = 2, nullable = false)
  private BigDecimal basicSalary = BigDecimal.ZERO;

  @Column(precision = 12, scale = 2) private BigDecimal hra = BigDecimal.ZERO;
  @Column(precision = 12, scale = 2) private BigDecimal da = BigDecimal.ZERO;
  @Column(name = "medical_allowance", precision = 12, scale = 2) private BigDecimal medicalAllowance = BigDecimal.ZERO;
  @Column(precision = 12, scale = 2) private BigDecimal conveyance = BigDecimal.ZERO;
  @Column(name = "special_allowance", precision = 12, scale = 2) private BigDecimal specialAllowance = BigDecimal.ZERO;

  @Column(precision = 12, scale = 2) private BigDecimal pf = BigDecimal.ZERO;
  @Column(precision = 12, scale = 2) private BigDecimal esi = BigDecimal.ZERO;
  @Column(name = "professional_tax", precision = 12, scale = 2) private BigDecimal professionalTax = BigDecimal.ZERO;
  @Column(name = "income_tax", precision = 12, scale = 2) private BigDecimal incomeTax = BigDecimal.ZERO;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getEmployeePk() { return employeePk; }
  public void setEmployeePk(String employeePk) { this.employeePk = employeePk; }
  public String getEmployeeId() { return employeeId; }
  public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
  public String getEmployeeName() { return employeeName; }
  public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
  public BigDecimal getBasicSalary() { return basicSalary; }
  public void setBasicSalary(BigDecimal basicSalary) { this.basicSalary = basicSalary; }
  public BigDecimal getHra() { return hra; }
  public void setHra(BigDecimal hra) { this.hra = hra; }
  public BigDecimal getDa() { return da; }
  public void setDa(BigDecimal da) { this.da = da; }
  public BigDecimal getMedicalAllowance() { return medicalAllowance; }
  public void setMedicalAllowance(BigDecimal medicalAllowance) { this.medicalAllowance = medicalAllowance; }
  public BigDecimal getConveyance() { return conveyance; }
  public void setConveyance(BigDecimal conveyance) { this.conveyance = conveyance; }
  public BigDecimal getSpecialAllowance() { return specialAllowance; }
  public void setSpecialAllowance(BigDecimal specialAllowance) { this.specialAllowance = specialAllowance; }
  public BigDecimal getPf() { return pf; }
  public void setPf(BigDecimal pf) { this.pf = pf; }
  public BigDecimal getEsi() { return esi; }
  public void setEsi(BigDecimal esi) { this.esi = esi; }
  public BigDecimal getProfessionalTax() { return professionalTax; }
  public void setProfessionalTax(BigDecimal professionalTax) { this.professionalTax = professionalTax; }
  public BigDecimal getIncomeTax() { return incomeTax; }
  public void setIncomeTax(BigDecimal incomeTax) { this.incomeTax = incomeTax; }
}
