package com.medtrack.schedule.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "doctor_availability")
public class DoctorAvailabilityEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String doctorId;
  private Instant startsAt;
  private Instant endsAt;
  private String availabilityType = "AVAILABLE";
  private String reason;
  private Instant createdAt = Instant.now();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public Instant getStartsAt() { return startsAt; }
  public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
  public Instant getEndsAt() { return endsAt; }
  public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
  public String getAvailabilityType() { return availabilityType; }
  public void setAvailabilityType(String availabilityType) { this.availabilityType = availabilityType; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
