package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** Personal information — table doctor_personal (doctor_id is PK used as FK elsewhere). */
@Entity
@Table(name = "doctor_personal")
public class DoctorPersonalEntity {

  @Id
  @Column(name = "doctor_id", length = 40)
  private String doctorId;

  /** FK -> svc.hospitals(id) */
  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId = 10001L;

  private String firstName;
  private String middleName;
  private String lastName;
  private String gender;
  private LocalDate dateOfBirth;

  @Column(columnDefinition = "TEXT")
  private String profilePhoto;

  private String bloodGroup;
  private String maritalStatus;

  /** Login password for User ID sign-in. */
  @Column(name = "login_password")
  private String loginPassword;

  @Column(nullable = false)
  private Instant createdDate = Instant.now();

  private Instant updatedDate = Instant.now();

  @PreUpdate
  void onUpdate() {
    updatedDate = Instant.now();
  }

  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getFirstName() { return firstName; }
  public void setFirstName(String firstName) { this.firstName = firstName; }
  public String getMiddleName() { return middleName; }
  public void setMiddleName(String middleName) { this.middleName = middleName; }
  public String getLastName() { return lastName; }
  public void setLastName(String lastName) { this.lastName = lastName; }
  public String getGender() { return gender; }
  public void setGender(String gender) { this.gender = gender; }
  public LocalDate getDateOfBirth() { return dateOfBirth; }
  public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
  public String getProfilePhoto() { return profilePhoto; }
  public void setProfilePhoto(String profilePhoto) { this.profilePhoto = profilePhoto; }
  public String getBloodGroup() { return bloodGroup; }
  public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }
  public String getMaritalStatus() { return maritalStatus; }
  public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }
  public String getLoginPassword() { return loginPassword; }
  public void setLoginPassword(String loginPassword) { this.loginPassword = loginPassword; }
  public Instant getCreatedDate() { return createdDate; }
  public void setCreatedDate(Instant createdDate) { this.createdDate = createdDate; }
  public Instant getUpdatedDate() { return updatedDate; }
  public void setUpdatedDate(Instant updatedDate) { this.updatedDate = updatedDate; }
}
