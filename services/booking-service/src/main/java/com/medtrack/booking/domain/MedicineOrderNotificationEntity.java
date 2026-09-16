package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "medicine_order_notifications")
public class MedicineOrderNotificationEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "store_id")
  private String storeId;

  @Column(name = "patient_phone")
  private String patientPhone;

  private String audience;

  @Column(name = "order_id")
  private String orderId;

  private String title;
  private String message;

  @Column(name = "read_flag")
  private boolean readFlag;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getStoreId() { return storeId; }
  public void setStoreId(String storeId) { this.storeId = storeId; }
  public String getPatientPhone() { return patientPhone; }
  public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
  public String getAudience() { return audience; }
  public void setAudience(String audience) { this.audience = audience; }
  public String getOrderId() { return orderId; }
  public void setOrderId(String orderId) { this.orderId = orderId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }
  public boolean isReadFlag() { return readFlag; }
  public void setReadFlag(boolean readFlag) { this.readFlag = readFlag; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
