package com.medtrack.hrm.domain;

import jakarta.persistence.*;

@Entity(name = "HrmDepartmentEntity")
@Table(name = "hrm_departments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_hrm_dept_name_doctor", columnNames = {"doctor_id", "name"})
})
public class DepartmentEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 120)
  private String departmentHead;

  @Column(length = 500)
  private String description;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDepartmentHead() { return departmentHead; }
  public void setDepartmentHead(String departmentHead) { this.departmentHead = departmentHead; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
}
