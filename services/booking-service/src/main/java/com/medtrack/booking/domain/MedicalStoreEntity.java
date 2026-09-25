package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "medical_stores")
public class MedicalStoreEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "store_code", nullable = false, length = 40)
  private String storeCode;

  @Column(name = "store_name", nullable = false)
  private String storeName;

  private String phone;
  private String address;

  @Column(length = 120)
  private String city;

  @Column(length = 120)
  private String state;

  @Column(name = "upi_id")
  private String upiId;
  private String status = "ACTIVE";

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "created_by")
  private String createdBy;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "updated_by")
  private String updatedBy;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getStoreCode() { return storeCode; }
  public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
  public String getStoreName() { return storeName; }
  public void setStoreName(String storeName) { this.storeName = storeName; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public String getAddress() { return address; }
  public void setAddress(String address) { this.address = address; }
  public String getCity() { return city; }
  public void setCity(String city) { this.city = city; }
  public String getState() { return state; }
  public void setState(String state) { this.state = state; }
  public String getUpiId() { return upiId; }
  public void setUpiId(String upiId) { this.upiId = upiId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public String getUpdatedBy() { return updatedBy; }
  public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
