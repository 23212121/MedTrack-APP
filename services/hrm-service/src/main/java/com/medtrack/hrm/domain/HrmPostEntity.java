package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    schema = "svc",
    name = "hrm_posts",
    indexes = {@Index(name = "idx_hrm_posts_hospital_doctor", columnList = "hospital_id, doctor_id")})
public class HrmPostEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "doctor_id", nullable = false, length = 64)
  private String doctorId;

  @Column(nullable = false, length = 240)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(nullable = false)
  private Integer likes = 0;

  @Column(nullable = false)
  private Integer comments = 0;

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
    if (likes == null) likes = 0;
    if (comments == null) comments = 0;
  }

  @PreUpdate
  protected void onUpdate() {
    updateDate = Instant.now();
    if (updateUser == null || updateUser.isBlank()) updateUser = "system";
  }

  public void touchAudit(Long hospitalId, String doctorId, String user) {
    if (hospitalId != null) this.hospitalId = hospitalId;
    if (doctorId != null && !doctorId.isBlank()) this.doctorId = doctorId;
    if (user != null && !user.isBlank()) {
      if (this.creationUser == null || this.creationUser.isBlank()) this.creationUser = user;
      this.updateUser = user;
    }
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }
  public Integer getLikes() { return likes; }
  public void setLikes(Integer likes) { this.likes = likes; }
  public Integer getComments() { return comments; }
  public void setComments(Integer comments) { this.comments = comments; }
  public Instant getCreationDate() { return creationDate; }
  public void setCreationDate(Instant creationDate) { this.creationDate = creationDate; }
  public String getCreationUser() { return creationUser; }
  public void setCreationUser(String creationUser) { this.creationUser = creationUser; }
  public Instant getUpdateDate() { return updateDate; }
  public void setUpdateDate(Instant updateDate) { this.updateDate = updateDate; }
  public String getUpdateUser() { return updateUser; }
  public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
}
