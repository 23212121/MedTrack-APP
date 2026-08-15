package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bookings")
public class BookingEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /** FK -> svc.hospitals(id) */
  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "doctor_id", nullable = false, length = 64)
  private String doctorId;

  @Column(nullable = false)
  private String patientName;

  @Column(nullable = false)
  private String patientPhone;

  private Integer patientAge;
  private String gender;
  private String address;
  private String reason;

  @Column(nullable = false)
  private LocalDate appointmentDate;

  @Column(nullable = false)
  private Instant appointmentTime;

  private Integer tokenNumber;

  @Column(nullable = false)
  private String status = "BOOKED";

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  private Instant updatedAt = Instant.now();

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public String getPatientName() { return patientName; }
  public void setPatientName(String patientName) { this.patientName = patientName; }
  public String getPatientPhone() { return patientPhone; }
  public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
  public Integer getPatientAge() { return patientAge; }
  public void setPatientAge(Integer patientAge) { this.patientAge = patientAge; }
  public String getGender() { return gender; }
  public void setGender(String gender) { this.gender = gender; }
  public String getAddress() { return address; }
  public void setAddress(String address) { this.address = address; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public LocalDate getAppointmentDate() { return appointmentDate; }
  public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
  public Instant getAppointmentTime() { return appointmentTime; }
  public void setAppointmentTime(Instant appointmentTime) { this.appointmentTime = appointmentTime; }
  public Integer getTokenNumber() { return tokenNumber; }
  public void setTokenNumber(Integer tokenNumber) { this.tokenNumber = tokenNumber; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
