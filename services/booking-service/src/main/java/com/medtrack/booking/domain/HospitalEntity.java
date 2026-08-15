package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Hospital master. Unique numeric {@code id} starts at 10001 and increases by 1.
 */
@Entity
@Table(
    name = "hospitals",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_hospitals_code", columnNames = "hospital_code")
    })
public class HospitalEntity {
  /** Unique hospital ID — assigned from 10001 upward (PRIMARY KEY). */
  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  /** Same value as {@link #id}; UNIQUE for FK naming consistency. */
  @Column(name = "hospital_id", nullable = false, unique = true)
  private Long hospitalId;

  @Column(name = "hospital_code", nullable = false, length = 40)
  private String hospitalCode;

  @Column(name = "hospital_name", nullable = false)
  private String hospitalName;

  @Column(name = "hospital_type")
  private String hospitalType;

  @Column(name = "registration_number")
  private String registrationNumber;

  private String email;

  @Column(name = "primary_contact")
  private String primaryContact;

  private String city;
  private String state;
  private String country;

  @Column(name = "subscription_plan")
  private String subscriptionPlan;

  /** Hospital admin login identity (email or user id). */
  @Column(name = "admin_email")
  private String adminEmail;

  /** Hospital admin login password (set at registration). */
  @Column(name = "admin_password")
  private String adminPassword;

  /** PENDING_VERIFICATION | APPROVED | REJECTED */
  @Column(nullable = false)
  private String status = "PENDING_VERIFICATION";

  @Column(name = "registration_json", columnDefinition = "TEXT")
  private String registrationJson;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at")
  private Instant updatedAt = Instant.now();

  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }

  public Long getId() { return id; }
  public void setId(Long id) {
    this.id = id;
    this.hospitalId = id;
  }
  public Long getHospitalId() { return hospitalId != null ? hospitalId : id; }
  public void setHospitalId(Long hospitalId) {
    this.hospitalId = hospitalId;
    if (this.id == null) this.id = hospitalId;
  }
  public String getHospitalCode() { return hospitalCode; }
  public void setHospitalCode(String hospitalCode) { this.hospitalCode = hospitalCode; }
  public String getHospitalName() { return hospitalName; }
  public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }
  public String getHospitalType() { return hospitalType; }
  public void setHospitalType(String hospitalType) { this.hospitalType = hospitalType; }
  public String getRegistrationNumber() { return registrationNumber; }
  public void setRegistrationNumber(String registrationNumber) {
    this.registrationNumber = registrationNumber;
  }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPrimaryContact() { return primaryContact; }
  public void setPrimaryContact(String primaryContact) { this.primaryContact = primaryContact; }
  public String getCity() { return city; }
  public void setCity(String city) { this.city = city; }
  public String getState() { return state; }
  public void setState(String state) { this.state = state; }
  public String getCountry() { return country; }
  public void setCountry(String country) { this.country = country; }
  public String getSubscriptionPlan() { return subscriptionPlan; }
  public void setSubscriptionPlan(String subscriptionPlan) {
    this.subscriptionPlan = subscriptionPlan;
  }
  public String getAdminEmail() { return adminEmail; }
  public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
  public String getAdminPassword() { return adminPassword; }
  public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getRegistrationJson() { return registrationJson; }
  public void setRegistrationJson(String registrationJson) {
    this.registrationJson = registrationJson;
  }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
