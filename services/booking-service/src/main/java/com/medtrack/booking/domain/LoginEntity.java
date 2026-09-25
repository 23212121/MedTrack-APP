package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Unified login credentials — svc.login (hospital / doctor / patient). */
@Entity
@Table(name = "login")
public class LoginEntity {
  @Id
  @Column(length = 64)
  private String id;

  /** HOSPITAL | USER | PATIENT */
  @Column(name = "login_type", nullable = false, length = 20)
  private String loginType;

  /** Hospital id, doctor id, phone, or username used at login. */
  @Column(name = "login_id", nullable = false, length = 100)
  private String loginId;

  @Column(name = "password", nullable = false, length = 255)
  private String password;

  @Column(name = "hospital_id")
  private Long hospitalId;

  @Column(name = "doctor_id", length = 64)
  private String doctorId;

  @Column(name = "display_name", length = 255)
  private String displayName;

  /** 0 = Inactive, 1 = Active — FK to svc.status.status_id */
  @Column(nullable = false)
  private Integer status = LoginStatus.ACTIVE;

  @Column(name = "creation_date", nullable = false)
  private Instant creationDate = Instant.now();

  @Column(name = "creation_user", length = 100)
  private String creationUser;

  @Column(name = "update_date")
  private Instant updateDate;

  @Column(name = "update_user", length = 100)
  private String updateUser;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getLoginType() {
    return loginType;
  }

  public void setLoginType(String loginType) {
    this.loginType = loginType;
  }

  public String getLoginId() {
    return loginId;
  }

  public void setLoginId(String loginId) {
    this.loginId = loginId;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Long getHospitalId() {
    return hospitalId;
  }

  public void setHospitalId(Long hospitalId) {
    this.hospitalId = hospitalId;
  }

  public String getDoctorId() {
    return doctorId;
  }

  public void setDoctorId(String doctorId) {
    this.doctorId = doctorId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public Integer getStatus() {
    return status;
  }

  public void setStatus(Integer status) {
    this.status = status;
  }

  public Instant getCreationDate() {
    return creationDate;
  }

  public void setCreationDate(Instant creationDate) {
    this.creationDate = creationDate;
  }

  public String getCreationUser() {
    return creationUser;
  }

  public void setCreationUser(String creationUser) {
    this.creationUser = creationUser;
  }

  public Instant getUpdateDate() {
    return updateDate;
  }

  public void setUpdateDate(Instant updateDate) {
    this.updateDate = updateDate;
  }

  public String getUpdateUser() {
    return updateUser;
  }

  public void setUpdateUser(String updateUser) {
    this.updateUser = updateUser;
  }
}
