package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "patient_reports")
public class PatientReportEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "patient_phone", nullable = false, length = 32)
  private String patientPhone;

  @Column(name = "patient_name", length = 255)
  private String patientName;

  @Column(name = "hospital_id")
  private Long hospitalId;

  @Column(name = "doctor_id", length = 64)
  private String doctorId;

  @Column(name = "doctor_name", length = 255)
  private String doctorName;

  @Column(name = "visit_id", length = 64)
  private String visitId;

  /** LAB | PRESCRIPTION | DISCHARGE | IMAGING | OTHER */
  @Column(name = "report_type", nullable = false, length = 32)
  private String reportType;

  @Column(nullable = false, length = 500)
  private String title;

  @Column(length = 2000)
  private String description;

  @Column(name = "file_url", length = 1000)
  private String fileUrl;

  @Column(name = "report_date")
  private LocalDate reportDate;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPatientPhone() {
    return patientPhone;
  }

  public void setPatientPhone(String patientPhone) {
    this.patientPhone = patientPhone;
  }

  public String getPatientName() {
    return patientName;
  }

  public void setPatientName(String patientName) {
    this.patientName = patientName;
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

  public String getDoctorName() {
    return doctorName;
  }

  public void setDoctorName(String doctorName) {
    this.doctorName = doctorName;
  }

  public String getVisitId() {
    return visitId;
  }

  public void setVisitId(String visitId) {
    this.visitId = visitId;
  }

  public String getReportType() {
    return reportType;
  }

  public void setReportType(String reportType) {
    this.reportType = reportType;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getFileUrl() {
    return fileUrl;
  }

  public void setFileUrl(String fileUrl) {
    this.fileUrl = fileUrl;
  }

  public LocalDate getReportDate() {
    return reportDate;
  }

  public void setReportDate(LocalDate reportDate) {
    this.reportDate = reportDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
