package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    schema = "svc",
    name = "company_application_setting",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_company_app_setting_user_right",
            columnNames = {"hospital_id", "user_id", "right_code"}))
public class CompanyApplicationSettingEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "right_code", nullable = false, length = 80)
  private String rightCode;

  @Column(nullable = false)
  private Boolean allowed = true;

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
    if (allowed == null) allowed = true;
  }

  @PreUpdate
  protected void onUpdate() {
    updateDate = Instant.now();
    if (updateUser == null || updateUser.isBlank()) updateUser = "system";
  }

  public void touchAudit(Long hospitalId, String user) {
    if (hospitalId != null) this.hospitalId = hospitalId;
    if (user != null && !user.isBlank()) {
      if (this.creationUser == null || this.creationUser.isBlank()) this.creationUser = user;
      this.updateUser = user;
    }
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getRightCode() { return rightCode; }
  public void setRightCode(String rightCode) { this.rightCode = rightCode; }
  public Boolean getAllowed() { return allowed; }
  public void setAllowed(Boolean allowed) { this.allowed = allowed; }
  public Instant getCreationDate() { return creationDate; }
  public void setCreationDate(Instant creationDate) { this.creationDate = creationDate; }
  public String getCreationUser() { return creationUser; }
  public void setCreationUser(String creationUser) { this.creationUser = creationUser; }
  public Instant getUpdateDate() { return updateDate; }
  public void setUpdateDate(Instant updateDate) { this.updateDate = updateDate; }
  public String getUpdateUser() { return updateUser; }
  public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
}
