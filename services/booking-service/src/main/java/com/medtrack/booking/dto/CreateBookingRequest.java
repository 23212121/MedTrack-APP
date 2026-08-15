package com.medtrack.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

/** Request body for POST /api/bookings (insert into database). */
public class CreateBookingRequest {
  @NotBlank(message = "doctorId is required")
  private String doctorId;

  @NotBlank(message = "patientName is required")
  private String patientName;

  @NotBlank(message = "patientPhone is required")
  private String patientPhone;

  /** Optional — used to email booking confirmation when provided / known. */
  private String patientEmail;

  private Integer patientAge;
  private String gender;
  private String address;
  private String reason;

  @NotNull(message = "appointmentDate is required (YYYY-MM-DD)")
  private LocalDate appointmentDate;

  @NotNull(message = "appointmentTime is required (ISO-8601 instant)")
  private Instant appointmentTime;

  /** HOSPITAL when desk books; PATIENT for self-service. */
  private String bookedBy;

  /** Hospital id (optional — resolved from doctor_clinic when omitted). */
  private Long hospitalId;

  /** Audit — who created the record (username / PATIENT / HOSPITAL). */
  private String createdBy;

  /** Patient UUID from login session — stored in appointments.patient_id. */
  private String patientId;

  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public String getPatientName() { return patientName; }
  public void setPatientName(String patientName) { this.patientName = patientName; }
  public String getPatientPhone() { return patientPhone; }
  public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
  public String getPatientEmail() { return patientEmail; }
  public void setPatientEmail(String patientEmail) { this.patientEmail = patientEmail; }
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
  public String getBookedBy() { return bookedBy; }
  public void setBookedBy(String bookedBy) { this.bookedBy = bookedBy; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public String getPatientId() { return patientId; }
  public void setPatientId(String patientId) { this.patientId = patientId; }
}
