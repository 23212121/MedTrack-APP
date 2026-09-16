package com.medtrack.hrm.domain;

import jakarta.persistence.*;

@Entity
@Table(schema = "svc", name = "hrm_candidates")
public class CandidateEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 20)
  private String mobile;

  @Column(length = 120)
  private String email;

  @Column(name = "applied_position", length = 120)
  private String appliedPosition;

  @Column(length = 500)
  private String resume;

  @Column(length = 40)
  private String status = "Applied";

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getMobile() { return mobile; }
  public void setMobile(String mobile) { this.mobile = mobile; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getAppliedPosition() { return appliedPosition; }
  public void setAppliedPosition(String appliedPosition) { this.appliedPosition = appliedPosition; }
  public String getResume() { return resume; }
  public void setResume(String resume) { this.resume = resume; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
