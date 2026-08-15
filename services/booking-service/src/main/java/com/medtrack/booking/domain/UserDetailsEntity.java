package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Patient/User registration — maps to svc.user_details (USER_DETAILS spec). */
@Entity
@Table(name = "user_details")
public class UserDetailsEntity {
  @Id
  @Column(name = "user_id", length = 20, nullable = false)
  private String userId;

  @Column(name = "user_name", length = 100, nullable = false, unique = true)
  private String userName;

  /** Optional mobile — login with phone number (digits only). */
  @Column(length = 32, unique = true)
  private String phone;

  /** Optional — used for booking confirmation email. */
  @Column(length = 150)
  private String email;

  @Column(length = 255, nullable = false)
  private String password;

  @Column(name = "created_by", length = 50)
  private String createdBy;

  @Column(name = "created_date", nullable = false)
  private Instant createdDate = Instant.now();

  @Column(name = "updated_by", length = 50)
  private String updatedBy;

  @Column(name = "updated_date")
  private Instant updatedDate;

  @PreUpdate
  void onUpdate() {
    updatedDate = Instant.now();
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getCreatedDate() {
    return createdDate;
  }

  public void setCreatedDate(Instant createdDate) {
    this.createdDate = createdDate;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Instant getUpdatedDate() {
    return updatedDate;
  }

  public void setUpdatedDate(Instant updatedDate) {
    this.updatedDate = updatedDate;
  }
}
