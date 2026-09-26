package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "doctor_professional")
public class DoctorProfessionalEntity {
  @Id
  @Column(name = "doctor_id", length = 40)
  private String doctorId;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId = 10001L;

  private String medicalRegistrationNumber;
  private String medicalCouncilName;
  private LocalDate registrationDate;
  private LocalDate registrationValidUntil;
  private Integer yearsOfExperience;
  private String currentDesignation;
  private String department;
  private String specialization;
  private String subSpecialization;
  private String qualification;
  private String medicalCollege;
  private Integer graduationYear;

  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getMedicalRegistrationNumber() { return medicalRegistrationNumber; }
  public void setMedicalRegistrationNumber(String medicalRegistrationNumber) {
    this.medicalRegistrationNumber = medicalRegistrationNumber;
  }
  public String getMedicalCouncilName() { return medicalCouncilName; }
  public void setMedicalCouncilName(String medicalCouncilName) {
    this.medicalCouncilName = medicalCouncilName;
  }
  public LocalDate getRegistrationDate() { return registrationDate; }
  public void setRegistrationDate(LocalDate registrationDate) {
    this.registrationDate = registrationDate;
  }
  public LocalDate getRegistrationValidUntil() { return registrationValidUntil; }
  public void setRegistrationValidUntil(LocalDate registrationValidUntil) {
    this.registrationValidUntil = registrationValidUntil;
  }
  public Integer getYearsOfExperience() { return yearsOfExperience; }
  public void setYearsOfExperience(Integer yearsOfExperience) {
    this.yearsOfExperience = yearsOfExperience;
  }
  public String getCurrentDesignation() { return currentDesignation; }
  public void setCurrentDesignation(String currentDesignation) {
    this.currentDesignation = currentDesignation;
  }
  public String getDepartment() { return department; }
  public void setDepartment(String department) { this.department = department; }
  public String getSpecialization() { return specialization; }
  public void setSpecialization(String specialization) { this.specialization = specialization; }
  public String getSubSpecialization() { return subSpecialization; }
  public void setSubSpecialization(String subSpecialization) {
    this.subSpecialization = subSpecialization;
  }
  public String getQualification() { return qualification; }
  public void setQualification(String qualification) { this.qualification = qualification; }
  public String getMedicalCollege() { return medicalCollege; }
  public void setMedicalCollege(String medicalCollege) { this.medicalCollege = medicalCollege; }
  public Integer getGraduationYear() { return graduationYear; }
  public void setGraduationYear(Integer graduationYear) { this.graduationYear = graduationYear; }
}
