package com.medtrack.booking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "doctor_clinic")
public class DoctorClinicEntity {
  @Id
  @Column(name = "doctor_id", length = 40)
  private String doctorId;

  private String hospitalName;
  private String clinicName;
  private Long hospitalId;
  private String branch;
  private String consultationType;
  private Double consultationFee;
  private Double followUpFee;
  private String availableDays;
  private String availableTimeSlots;

  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public String getHospitalName() { return hospitalName; }
  public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }
  public String getClinicName() { return clinicName; }
  public void setClinicName(String clinicName) { this.clinicName = clinicName; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getBranch() { return branch; }
  public void setBranch(String branch) { this.branch = branch; }
  public String getConsultationType() { return consultationType; }
  public void setConsultationType(String consultationType) {
    this.consultationType = consultationType;
  }
  public Double getConsultationFee() { return consultationFee; }
  public void setConsultationFee(Double consultationFee) { this.consultationFee = consultationFee; }
  public Double getFollowUpFee() { return followUpFee; }
  public void setFollowUpFee(Double followUpFee) { this.followUpFee = followUpFee; }
  public String getAvailableDays() { return availableDays; }
  public void setAvailableDays(String availableDays) { this.availableDays = availableDays; }
  public String getAvailableTimeSlots() { return availableTimeSlots; }
  public void setAvailableTimeSlots(String availableTimeSlots) {
    this.availableTimeSlots = availableTimeSlots;
  }
}
