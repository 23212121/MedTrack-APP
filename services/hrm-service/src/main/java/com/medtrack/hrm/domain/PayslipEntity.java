package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "hrm_payslips", uniqueConstraints = {
    @UniqueConstraint(name = "uk_hrm_payslip", columnNames = {"employee_id", "pay_month", "pay_year", "doctor_id"})
})
public class PayslipEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "employee_pk", nullable = false, length = 64)
  private String employeePk;

  @Column(name = "employee_id", nullable = false, length = 40)
  private String employeeId;

  @Column(name = "employee_name", length = 160)
  private String employeeName;

  @Column(name = "pay_month", nullable = false)
  private Integer payMonth;

  @Column(name = "pay_year", nullable = false)
  private Integer payYear;

  @Column(name = "working_days")
  private Integer workingDays = 30;

  @Column(name = "present_days")
  private Integer presentDays = 30;

  @Column(name = "absent_days")
  private Integer absentDays = 0;

  @Column(name = "gross_earnings", precision = 12, scale = 2)
  private BigDecimal grossEarnings = BigDecimal.ZERO;

  @Column(name = "total_deductions", precision = 12, scale = 2)
  private BigDecimal totalDeductions = BigDecimal.ZERO;

  @Column(name = "leave_deduction", precision = 12, scale = 2)
  private BigDecimal leaveDeduction = BigDecimal.ZERO;

  @Column(name = "net_salary", precision = 12, scale = 2)
  private BigDecimal netSalary = BigDecimal.ZERO;

  @Column(name = "earnings_json", length = 2000)
  private String earningsJson;

  @Column(name = "deductions_json", length = 2000)
  private String deductionsJson;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getEmployeePk() { return employeePk; }
  public void setEmployeePk(String employeePk) { this.employeePk = employeePk; }
  public String getEmployeeId() { return employeeId; }
  public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
  public String getEmployeeName() { return employeeName; }
  public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
  public Integer getPayMonth() { return payMonth; }
  public void setPayMonth(Integer payMonth) { this.payMonth = payMonth; }
  public Integer getPayYear() { return payYear; }
  public void setPayYear(Integer payYear) { this.payYear = payYear; }
  public Integer getWorkingDays() { return workingDays; }
  public void setWorkingDays(Integer workingDays) { this.workingDays = workingDays; }
  public Integer getPresentDays() { return presentDays; }
  public void setPresentDays(Integer presentDays) { this.presentDays = presentDays; }
  public Integer getAbsentDays() { return absentDays; }
  public void setAbsentDays(Integer absentDays) { this.absentDays = absentDays; }
  public BigDecimal getGrossEarnings() { return grossEarnings; }
  public void setGrossEarnings(BigDecimal grossEarnings) { this.grossEarnings = grossEarnings; }
  public BigDecimal getTotalDeductions() { return totalDeductions; }
  public void setTotalDeductions(BigDecimal totalDeductions) { this.totalDeductions = totalDeductions; }
  public BigDecimal getLeaveDeduction() { return leaveDeduction; }
  public void setLeaveDeduction(BigDecimal leaveDeduction) { this.leaveDeduction = leaveDeduction; }
  public BigDecimal getNetSalary() { return netSalary; }
  public void setNetSalary(BigDecimal netSalary) { this.netSalary = netSalary; }
  public String getEarningsJson() { return earningsJson; }
  public void setEarningsJson(String earningsJson) { this.earningsJson = earningsJson; }
  public String getDeductionsJson() { return deductionsJson; }
  public void setDeductionsJson(String deductionsJson) { this.deductionsJson = deductionsJson; }
}
