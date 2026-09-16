package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    schema = "svc",
    name = "hrm_post_likes",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_hrm_post_like_user",
            columnNames = {"post_id", "user_id"}))
public class HrmPostLikeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "post_id", nullable = false, length = 36)
  private String postId;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "user_name", length = 160)
  private String userName;

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
  public String getPostId() { return postId; }
  public void setPostId(String postId) { this.postId = postId; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getUserName() { return userName; }
  public void setUserName(String userName) { this.userName = userName; }
  public Instant getCreationDate() { return creationDate; }
  public void setCreationDate(Instant creationDate) { this.creationDate = creationDate; }
  public String getCreationUser() { return creationUser; }
  public void setCreationUser(String creationUser) { this.creationUser = creationUser; }
  public Instant getUpdateDate() { return updateDate; }
  public void setUpdateDate(Instant updateDate) { this.updateDate = updateDate; }
  public String getUpdateUser() { return updateUser; }
  public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
}
