package com.medtrack.hrm.domain;

import jakarta.persistence.*;

@Entity
@Table(schema = "svc", name = "hrm_leave_types", uniqueConstraints = {
    @UniqueConstraint(name = "uk_hrm_leave_type_doctor", columnNames = {"doctor_id", "name"})
})
public class LeaveTypeEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(name = "annual_quota")
  private Integer annualQuota = 0;

  @Column(length = 300)
  private String description;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public Integer getAnnualQuota() { return annualQuota; }
  public void setAnnualQuota(Integer annualQuota) { this.annualQuota = annualQuota; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
}
