package com.medtrack.hrm.domain;

import jakarta.persistence.*;

@Entity
@Table(schema = "svc", name = "hrm_designations", uniqueConstraints = {
    @UniqueConstraint(name = "uk_hrm_desig_name_doctor", columnNames = {"doctor_id", "name"})
})
public class DesignationEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "department_id", length = 64)
  private String departmentId;

  @Column(length = 120)
  private String departmentName;

  @Column(name = "salary_grade", length = 40)
  private String salaryGrade;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDepartmentId() { return departmentId; }
  public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
  public String getDepartmentName() { return departmentName; }
  public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
  public String getSalaryGrade() { return salaryGrade; }
  public void setSalaryGrade(String salaryGrade) { this.salaryGrade = salaryGrade; }
}
