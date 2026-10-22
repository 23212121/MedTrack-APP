package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "lab_bookings")
public class LabBookingEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "booking_number", nullable = false, length = 40)
  private String bookingNumber;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "patient_id", length = 64)
  private String patientId;

  @Column(name = "patient_name", nullable = false)
  private String patientName;

  @Column(name = "patient_phone", nullable = false, length = 32)
  private String patientPhone;

  @Column(nullable = false, length = 30)
  private String mode;

  @Column(name = "collection_address", length = 500)
  private String collectionAddress;

  @Column(name = "patient_latitude")
  private Double patientLatitude;

  @Column(name = "patient_longitude")
  private Double patientLongitude;

  @Column(name = "trip_distance_km")
  private Double tripDistanceKm;

  @Column(name = "trip_charge")
  private Double tripCharge;

  @Column(length = 1000)
  private String notes;

  @Column(name = "preferred_at")
  private Instant preferredAt;

  @Column(nullable = false, length = 40)
  private String status = "PENDING";

  @Column(name = "assigned_lab_id", length = 64)
  private String assignedLabId;

  @Column(name = "assigned_lab_name")
  private String assignedLabName;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "visit_time")
  private Instant visitTime;

  @Column(name = "token_number")
  private Integer tokenNumber;

  @Column(name = "expected_visit_at")
  private Instant expectedVisitAt;

  @Column(name = "queue_date")
  private LocalDate queueDate;

  @Column(name = "tech_reached_at")
  private Instant techReachedAt;

  @Column(name = "cancel_reason", length = 500)
  private String cancelReason;

  @Column(name = "cancel_charge")
  private Double cancelCharge;

  @Column(name = "distance_km")
  private Double distanceKm;

  @Column(name = "cancelled_by", length = 100)
  private String cancelledBy;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "completed_by", length = 100)
  private String completedBy;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "created_by", length = 100)
  private String createdBy;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "updated_by", length = 100)
  private String updatedBy;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getBookingNumber() { return bookingNumber; }
  public void setBookingNumber(String bookingNumber) { this.bookingNumber = bookingNumber; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getPatientId() { return patientId; }
  public void setPatientId(String patientId) { this.patientId = patientId; }
  public String getPatientName() { return patientName; }
  public void setPatientName(String patientName) { this.patientName = patientName; }
  public String getPatientPhone() { return patientPhone; }
  public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
  public String getMode() { return mode; }
  public void setMode(String mode) { this.mode = mode; }
  public String getCollectionAddress() { return collectionAddress; }
  public void setCollectionAddress(String collectionAddress) { this.collectionAddress = collectionAddress; }
  public Double getPatientLatitude() { return patientLatitude; }
  public void setPatientLatitude(Double patientLatitude) { this.patientLatitude = patientLatitude; }
  public Double getPatientLongitude() { return patientLongitude; }
  public void setPatientLongitude(Double patientLongitude) { this.patientLongitude = patientLongitude; }
  public Double getTripDistanceKm() { return tripDistanceKm; }
  public void setTripDistanceKm(Double tripDistanceKm) { this.tripDistanceKm = tripDistanceKm; }
  public Double getTripCharge() { return tripCharge; }
  public void setTripCharge(Double tripCharge) { this.tripCharge = tripCharge; }
  public String getNotes() { return notes; }
  public void setNotes(String notes) { this.notes = notes; }
  public Instant getPreferredAt() { return preferredAt; }
  public void setPreferredAt(Instant preferredAt) { this.preferredAt = preferredAt; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getAssignedLabId() { return assignedLabId; }
  public void setAssignedLabId(String assignedLabId) { this.assignedLabId = assignedLabId; }
  public String getAssignedLabName() { return assignedLabName; }
  public void setAssignedLabName(String assignedLabName) { this.assignedLabName = assignedLabName; }
  public Instant getConfirmedAt() { return confirmedAt; }
  public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
  public Instant getVisitTime() { return visitTime; }
  public void setVisitTime(Instant visitTime) { this.visitTime = visitTime; }
  public Integer getTokenNumber() { return tokenNumber; }
  public void setTokenNumber(Integer tokenNumber) { this.tokenNumber = tokenNumber; }
  public Instant getExpectedVisitAt() { return expectedVisitAt; }
  public void setExpectedVisitAt(Instant expectedVisitAt) { this.expectedVisitAt = expectedVisitAt; }
  public LocalDate getQueueDate() { return queueDate; }
  public void setQueueDate(LocalDate queueDate) { this.queueDate = queueDate; }
  public Instant getTechReachedAt() { return techReachedAt; }
  public void setTechReachedAt(Instant techReachedAt) { this.techReachedAt = techReachedAt; }
  public String getCancelReason() { return cancelReason; }
  public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
  public Double getCancelCharge() { return cancelCharge; }
  public void setCancelCharge(Double cancelCharge) { this.cancelCharge = cancelCharge; }
  public Double getDistanceKm() { return distanceKm; }
  public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
  public String getCancelledBy() { return cancelledBy; }
  public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
  public Instant getCancelledAt() { return cancelledAt; }
  public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
  public Instant getCompletedAt() { return completedAt; }
  public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
  public String getCompletedBy() { return completedBy; }
  public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public String getUpdatedBy() { return updatedBy; }
  public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
