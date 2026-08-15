package com.medtrack.booking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "doctor_identity")
public class DoctorIdentityEntity {
  @Id
  @Column(name = "doctor_id", length = 40)
  private String doctorId;

  private String aadhaarNumber;
  private String panNumber;
  private String passportNumber;

  @Column(columnDefinition = "TEXT")
  private String governmentIdUpload;

  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public String getAadhaarNumber() { return aadhaarNumber; }
  public void setAadhaarNumber(String aadhaarNumber) { this.aadhaarNumber = aadhaarNumber; }
  public String getPanNumber() { return panNumber; }
  public void setPanNumber(String panNumber) { this.panNumber = panNumber; }
  public String getPassportNumber() { return passportNumber; }
  public void setPassportNumber(String passportNumber) { this.passportNumber = passportNumber; }
  public String getGovernmentIdUpload() { return governmentIdUpload; }
  public void setGovernmentIdUpload(String governmentIdUpload) {
    this.governmentIdUpload = governmentIdUpload;
  }
}
