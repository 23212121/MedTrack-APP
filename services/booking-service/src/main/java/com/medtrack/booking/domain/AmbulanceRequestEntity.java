package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ambulance_requests")
public class AmbulanceRequestEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "request_number", nullable = false, length = 40)
  private String requestNumber;

  @Column(name = "hospital_id")
  private Long hospitalId;

  @Column(name = "patient_id", length = 64)
  private String patientId;

  @Column(name = "patient_name")
  private String patientName;

  @Column(name = "patient_phone", length = 32)
  private String patientPhone;

  @Column(name = "patient_latitude", nullable = false)
  private double patientLatitude;

  @Column(name = "patient_longitude", nullable = false)
  private double patientLongitude;

  @Column(name = "ambulance_id", length = 64)
  private String ambulanceId;

  @Column(nullable = false, length = 20)
  private String status = "REQUESTED";

  @Column(name = "maps_url", length = 500)
  private String mapsUrl;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "arrived_at")
  private Instant arrivedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "distance_km")
  private Double distanceKm;

  @Column(name = "rate_per_km")
  private Double ratePerKm;

  @Column(name = "estimated_price")
  private Double estimatedPrice;

  @Column(name = "estimated_minutes")
  private Integer estimatedMinutes;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getRequestNumber() { return requestNumber; }
  public void setRequestNumber(String requestNumber) { this.requestNumber = requestNumber; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getPatientId() { return patientId; }
  public void setPatientId(String patientId) { this.patientId = patientId; }
  public String getPatientName() { return patientName; }
  public void setPatientName(String patientName) { this.patientName = patientName; }
  public String getPatientPhone() { return patientPhone; }
  public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
  public double getPatientLatitude() { return patientLatitude; }
  public void setPatientLatitude(double patientLatitude) { this.patientLatitude = patientLatitude; }
  public double getPatientLongitude() { return patientLongitude; }
  public void setPatientLongitude(double patientLongitude) { this.patientLongitude = patientLongitude; }
  public String getAmbulanceId() { return ambulanceId; }
  public void setAmbulanceId(String ambulanceId) { this.ambulanceId = ambulanceId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getMapsUrl() { return mapsUrl; }
  public void setMapsUrl(String mapsUrl) { this.mapsUrl = mapsUrl; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public Instant getAcceptedAt() { return acceptedAt; }
  public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
  public Instant getArrivedAt() { return arrivedAt; }
  public void setArrivedAt(Instant arrivedAt) { this.arrivedAt = arrivedAt; }
  public Instant getCompletedAt() { return completedAt; }
  public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
  public Instant getCancelledAt() { return cancelledAt; }
  public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
  public Double getDistanceKm() { return distanceKm; }
  public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
  public Double getRatePerKm() { return ratePerKm; }
  public void setRatePerKm(Double ratePerKm) { this.ratePerKm = ratePerKm; }
  public Double getEstimatedPrice() { return estimatedPrice; }
  public void setEstimatedPrice(Double estimatedPrice) { this.estimatedPrice = estimatedPrice; }
  public Integer getEstimatedMinutes() { return estimatedMinutes; }
  public void setEstimatedMinutes(Integer estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }
}
