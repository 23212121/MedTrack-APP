package com.medtrack.booking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "doctor_contact")
public class DoctorContactEntity {
  @Id
  @Column(name = "doctor_id", length = 40)
  private String doctorId;

  private String mobileNumber;
  private String alternateMobileNumber;
  private String email;
  private String emergencyContactNumber;

  @Column(columnDefinition = "TEXT")
  private String residentialAddress;

  private String city;
  private String state;
  private String country;
  private String postalCode;

  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public String getMobileNumber() { return mobileNumber; }
  public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
  public String getAlternateMobileNumber() { return alternateMobileNumber; }
  public void setAlternateMobileNumber(String alternateMobileNumber) {
    this.alternateMobileNumber = alternateMobileNumber;
  }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getEmergencyContactNumber() { return emergencyContactNumber; }
  public void setEmergencyContactNumber(String emergencyContactNumber) {
    this.emergencyContactNumber = emergencyContactNumber;
  }
  public String getResidentialAddress() { return residentialAddress; }
  public void setResidentialAddress(String residentialAddress) {
    this.residentialAddress = residentialAddress;
  }
  public String getCity() { return city; }
  public void setCity(String city) { this.city = city; }
  public String getState() { return state; }
  public void setState(String state) { this.state = state; }
  public String getCountry() { return country; }
  public void setCountry(String country) { this.country = country; }
  public String getPostalCode() { return postalCode; }
  public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
}
