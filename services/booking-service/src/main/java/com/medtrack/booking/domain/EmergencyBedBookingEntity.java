package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "emergency_bed_bookings")
public class EmergencyBedBookingEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "bed_id", nullable = false, length = 64)
  private String bedId;

  @Column(name = "bed_number", length = 20)
  private String bedNumber;

  @Column(name = "patient_name", nullable = false, length = 255)
  private String patientName;

  @Column(name = "patient_phone", length = 32)
  private String patientPhone;

  @Column(name = "patient_id", length = 64)
  private String patientId;

  @Column(name = "booked_by_login_id", length = 100)
  private String bookedByLoginId;

  @Column(name = "booked_by_type", length = 20)
  private String bookedByType;

  /** BOOKED | CANCELLED | DISCHARGED */
  @Column(nullable = false, length = 20)
  private String status = "BOOKED";

  private Double fees;

  @Column(name = "payment_id", length = 40)
  private String paymentId;

  @Column(name = "payment_status", length = 30)
  private String paymentStatus = "UNPAID";

  @Column(name = "payment_slip_path", length = 1000)
  private String paymentSlipPath;

  @Column(name = "payment_slip_name", length = 255)
  private String paymentSlipName;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at")
  private Instant updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Long getHospitalId() {
    return hospitalId;
  }

  public void setHospitalId(Long hospitalId) {
    this.hospitalId = hospitalId;
  }

  public String getBedId() {
    return bedId;
  }

  public void setBedId(String bedId) {
    this.bedId = bedId;
  }

  public String getBedNumber() {
    return bedNumber;
  }

  public void setBedNumber(String bedNumber) {
    this.bedNumber = bedNumber;
  }

  public String getPatientName() {
    return patientName;
  }

  public void setPatientName(String patientName) {
    this.patientName = patientName;
  }

  public String getPatientPhone() {
    return patientPhone;
  }

  public void setPatientPhone(String patientPhone) {
    this.patientPhone = patientPhone;
  }

  public String getPatientId() {
    return patientId;
  }

  public void setPatientId(String patientId) {
    this.patientId = patientId;
  }

  public String getBookedByLoginId() {
    return bookedByLoginId;
  }

  public void setBookedByLoginId(String bookedByLoginId) {
    this.bookedByLoginId = bookedByLoginId;
  }

  public String getBookedByType() {
    return bookedByType;
  }

  public void setBookedByType(String bookedByType) {
    this.bookedByType = bookedByType;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Double getFees() {
    return fees;
  }

  public void setFees(Double fees) {
    this.fees = fees;
  }

  public String getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(String paymentId) {
    this.paymentId = paymentId;
  }

  public String getPaymentStatus() {
    return paymentStatus;
  }

  public void setPaymentStatus(String paymentStatus) {
    this.paymentStatus = paymentStatus;
  }

  public String getPaymentSlipPath() {
    return paymentSlipPath;
  }

  public void setPaymentSlipPath(String paymentSlipPath) {
    this.paymentSlipPath = paymentSlipPath;
  }

  public String getPaymentSlipName() {
    return paymentSlipName;
  }

  public void setPaymentSlipName(String paymentSlipName) {
    this.paymentSlipName = paymentSlipName;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
