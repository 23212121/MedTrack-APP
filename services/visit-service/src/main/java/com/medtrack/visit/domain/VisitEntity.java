package com.medtrack.visit.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "visits")
public class VisitEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String clinicId;
  /** FK -> svc.hospitals(id) */
  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId = 10001L;
  private String patientId;
  private String patientName;
  private String patientPhone;
  private String patientEmail;
  private boolean smsConsent = true;
  private boolean emailConsent = true;
  private String doctorId;
  private String doctorName;
  private String status = "BOOKED";
  private Integer tokenNumber;
  private String reason;
  private Instant scheduledStart;
  private Instant scheduledEnd;
  private Instant checkedInAt;
  private Instant actualStart;
  private Instant actualEnd;
  private int delayMinutes;
  private Double baseFee;
  private int overtimeMinutes;
  private double overtimeFee;
  private Double totalFee;
  private String feeCurrency = "INR";
  private Instant createdAt = Instant.now();
  private Instant updatedAt = Instant.now();

  @PreUpdate
  void touch() { updatedAt = Instant.now(); }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getClinicId() { return clinicId; }
  public void setClinicId(String clinicId) { this.clinicId = clinicId; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getPatientId() { return patientId; }
  public void setPatientId(String patientId) { this.patientId = patientId; }
  public String getPatientName() { return patientName; }
  public void setPatientName(String patientName) { this.patientName = patientName; }
  public String getPatientPhone() { return patientPhone; }
  public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
  public String getPatientEmail() { return patientEmail; }
  public void setPatientEmail(String patientEmail) { this.patientEmail = patientEmail; }
  public boolean isSmsConsent() { return smsConsent; }
  public void setSmsConsent(boolean smsConsent) { this.smsConsent = smsConsent; }
  public boolean isEmailConsent() { return emailConsent; }
  public void setEmailConsent(boolean emailConsent) { this.emailConsent = emailConsent; }
  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public String getDoctorName() { return doctorName; }
  public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Integer getTokenNumber() { return tokenNumber; }
  public void setTokenNumber(Integer tokenNumber) { this.tokenNumber = tokenNumber; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public Instant getScheduledStart() { return scheduledStart; }
  public void setScheduledStart(Instant scheduledStart) { this.scheduledStart = scheduledStart; }
  public Instant getScheduledEnd() { return scheduledEnd; }
  public void setScheduledEnd(Instant scheduledEnd) { this.scheduledEnd = scheduledEnd; }
  public Instant getCheckedInAt() { return checkedInAt; }
  public void setCheckedInAt(Instant checkedInAt) { this.checkedInAt = checkedInAt; }
  public Instant getActualStart() { return actualStart; }
  public void setActualStart(Instant actualStart) { this.actualStart = actualStart; }
  public Instant getActualEnd() { return actualEnd; }
  public void setActualEnd(Instant actualEnd) { this.actualEnd = actualEnd; }
  public int getDelayMinutes() { return delayMinutes; }
  public void setDelayMinutes(int delayMinutes) { this.delayMinutes = delayMinutes; }
  public Double getBaseFee() { return baseFee; }
  public void setBaseFee(Double baseFee) { this.baseFee = baseFee; }
  public int getOvertimeMinutes() { return overtimeMinutes; }
  public void setOvertimeMinutes(int overtimeMinutes) { this.overtimeMinutes = overtimeMinutes; }
  public double getOvertimeFee() { return overtimeFee; }
  public void setOvertimeFee(double overtimeFee) { this.overtimeFee = overtimeFee; }
  public Double getTotalFee() { return totalFee; }
  public void setTotalFee(Double totalFee) { this.totalFee = totalFee; }
  public String getFeeCurrency() { return feeCurrency; }
  public void setFeeCurrency(String feeCurrency) { this.feeCurrency = feeCurrency; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
