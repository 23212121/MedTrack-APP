package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "emergency_beds")
public class EmergencyBedEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "bed_number", nullable = false, length = 20)
  private String bedNumber;

  @Column(name = "ward", length = 40)
  private String ward = "EMERGENCY";

  @Column(nullable = false)
  private Double fees = 2500d;

  /** AVAILABLE | BOOKED */
  @Column(nullable = false, length = 20)
  private String status = "AVAILABLE";

  @Column(length = 500)
  private String notes;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

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

  public String getBedNumber() {
    return bedNumber;
  }

  public void setBedNumber(String bedNumber) {
    this.bedNumber = bedNumber;
  }

  public String getWard() {
    return ward;
  }

  public void setWard(String ward) {
    this.ward = ward;
  }

  public Double getFees() {
    return fees;
  }

  public void setFees(Double fees) {
    this.fees = fees;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
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
}
