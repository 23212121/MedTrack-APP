package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "lab_booking_notifications")
public class LabBookingNotificationEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "hospital_id")
  private Long hospitalId;

  @Column(name = "lab_id", length = 64)
  private String labId;

  @Column(name = "patient_phone", length = 32)
  private String patientPhone;

  @Column(nullable = false, length = 20)
  private String audience;

  @Column(name = "booking_id", nullable = false, length = 64)
  private String bookingId;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, length = 1000)
  private String message;

  @Column(name = "read_flag", nullable = false)
  private boolean readFlag;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getLabId() { return labId; }
  public void setLabId(String labId) { this.labId = labId; }
  public String getPatientPhone() { return patientPhone; }
  public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
  public String getAudience() { return audience; }
  public void setAudience(String audience) { this.audience = audience; }
  public String getBookingId() { return bookingId; }
  public void setBookingId(String bookingId) { this.bookingId = bookingId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }
  public boolean isReadFlag() { return readFlag; }
  public void setReadFlag(boolean readFlag) { this.readFlag = readFlag; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
