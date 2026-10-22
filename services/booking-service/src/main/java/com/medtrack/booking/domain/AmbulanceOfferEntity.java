package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ambulance_offers")
public class AmbulanceOfferEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "request_id", nullable = false, length = 64)
  private String requestId;

  @Column(name = "ambulance_id", nullable = false, length = 64)
  private String ambulanceId;

  @Column(nullable = false, length = 20)
  private String status = "OFFERED";

  @Column(name = "distance_km")
  private Double distanceKm;

  @Column(name = "offered_at")
  private Instant offeredAt = Instant.now();

  @Column(name = "deadline_at", nullable = false)
  private Instant deadlineAt;

  @Column(name = "responded_at")
  private Instant respondedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getRequestId() { return requestId; }
  public void setRequestId(String requestId) { this.requestId = requestId; }
  public String getAmbulanceId() { return ambulanceId; }
  public void setAmbulanceId(String ambulanceId) { this.ambulanceId = ambulanceId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Double getDistanceKm() { return distanceKm; }
  public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
  public Instant getOfferedAt() { return offeredAt; }
  public void setOfferedAt(Instant offeredAt) { this.offeredAt = offeredAt; }
  public Instant getDeadlineAt() { return deadlineAt; }
  public void setDeadlineAt(Instant deadlineAt) { this.deadlineAt = deadlineAt; }
  public Instant getRespondedAt() { return respondedAt; }
  public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
}
