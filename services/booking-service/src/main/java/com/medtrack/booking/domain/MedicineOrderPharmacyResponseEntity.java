package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "medicine_order_pharmacy_response")
public class MedicineOrderPharmacyResponseEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "order_id", nullable = false)
  private String orderId;

  @Column(name = "store_id", nullable = false)
  private String storeId;

  private String action;
  private String reason;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getOrderId() { return orderId; }
  public void setOrderId(String orderId) { this.orderId = orderId; }
  public String getStoreId() { return storeId; }
  public void setStoreId(String storeId) { this.storeId = storeId; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
