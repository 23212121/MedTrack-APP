package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ambulances")
public class AmbulanceEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "hospital_id")
  private Long hospitalId;

  @Column(name = "driver_name", nullable = false)
  private String driverName;

  @Column(name = "mobile_number", nullable = false, length = 32)
  private String mobileNumber;

  private Double latitude;
  private Double longitude;

  @Column(name = "fcm_token", length = 512)
  private String fcmToken;

  @Column(name = "availability_status", nullable = false, length = 20)
  private String availabilityStatus = "OFFLINE";

  @Column(name = "last_location_update")
  private Instant lastLocationUpdate;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at")
  private Instant updatedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getDriverName() { return driverName; }
  public void setDriverName(String driverName) { this.driverName = driverName; }
  public String getMobileNumber() { return mobileNumber; }
  public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
  public Double getLatitude() { return latitude; }
  public void setLatitude(Double latitude) { this.latitude = latitude; }
  public Double getLongitude() { return longitude; }
  public void setLongitude(Double longitude) { this.longitude = longitude; }
  public String getFcmToken() { return fcmToken; }
  public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
  public String getAvailabilityStatus() { return availabilityStatus; }
  public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }
  public Instant getLastLocationUpdate() { return lastLocationUpdate; }
  public void setLastLocationUpdate(Instant lastLocationUpdate) { this.lastLocationUpdate = lastLocationUpdate; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
