package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** Primary appointment store — svc.appointments (normalized FKs only). */
@Entity
@Table(
    name = "appointments",
    indexes = {
      @Index(name = "idx_appointments_doctor", columnList = "doctor_id"),
      @Index(name = "idx_appointments_hospital", columnList = "hospital_id"),
      @Index(name = "idx_appointments_phone", columnList = "phone_number"),
      @Index(name = "idx_appointments_patient", columnList = "patient_id"),
      @Index(name = "idx_appointments_doctor_date", columnList = "doctor_id, appointment_date"),
      @Index(name = "idx_appointments_hospital_doctor", columnList = "hospital_id, doctor_id")
    })
public class AppointmentEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "appointment_id")
  private String id;

  /** Hospital when booked by staff; resolved from doctor when patient self-books. */
  @Column(name = "hospital_id")
  private Long hospitalId;

  @Column(name = "doctor_id", nullable = false, length = 64)
  private String doctorId;

  /**
   * Patient identifier — USR… user id, legacy patient UUID, or phone digits when walk-in.
   * Tracking by phone uses {@link #phoneNumber}, not this field alone.
   */
  @Column(name = "patient_id", nullable = false, length = 64)
  private String patientId;

  /** Normalized mobile used for /track?phone=… (hospital or patient booking). */
  @Column(name = "phone_number", length = 32)
  private String phoneNumber;

  @Column(name = "patient_name", nullable = false, length = 255)
  private String patientName;

  @Column(name = "patient_age")
  private Integer patientAge;

  @Column(length = 32)
  private String gender;

  @Column(length = 500)
  private String address;

  @Column(length = 1000)
  private String reason;

  @Column(name = "appointment_date", nullable = false)
  private LocalDate appointmentDate;

  @Column(name = "appointment_time", nullable = false)
  private Instant appointmentTime;

  @Column(name = "token_number")
  private Integer tokenNumber;

  @Column(nullable = false, length = 32)
  private String status = "BOOKED";

  /** HOSPITAL | PATIENT — who initiated the booking. */
  @Column(name = "booked_by", length = 32)
  private String bookedBy;

  /** Link to legacy bookings row (same transaction). */
  @Column(name = "booking_ref_id", length = 36)
  private String bookingRefId;

  @Column(name = "created_by", length = 50)
  private String createdBy;

  @Column(name = "created_date", nullable = false)
  private Instant createdDate = Instant.now();

  @Column(name = "updated_by", length = 50)
  private String updatedBy;

  @Column(name = "updated_date")
  private Instant updatedDate;

  @PreUpdate
  void onUpdate() {
    updatedDate = Instant.now();
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public String getPatientId() { return patientId; }
  public void setPatientId(String patientId) { this.patientId = patientId; }
  public String getPhoneNumber() { return phoneNumber; }
  public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
  public String getPatientName() { return patientName; }
  public void setPatientName(String patientName) { this.patientName = patientName; }
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
  public String getBookedBy() { return bookedBy; }
  public void setBookedBy(String bookedBy) { this.bookedBy = bookedBy; }
  public String getBookingRefId() { return bookingRefId; }
  public void setBookingRefId(String bookingRefId) { this.bookingRefId = bookingRefId; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public Instant getCreatedDate() { return createdDate; }
  public void setCreatedDate(Instant createdDate) { this.createdDate = createdDate; }
  public String getUpdatedBy() { return updatedBy; }
  public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
  public Instant getUpdatedDate() { return updatedDate; }
  public void setUpdatedDate(Instant updatedDate) { this.updatedDate = updatedDate; }
}
