package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "medicine_order_amount_history")
public class MedicineOrderAmountHistoryEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "order_id", nullable = false)
  private String orderId;

  private int version;

  @Column(name = "grand_total")
  private double grandTotal;

  @Column(name = "snapshot_json", columnDefinition = "TEXT")
  private String snapshotJson;

  private String status;
  private String reason;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "created_by")
  private String createdBy;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getOrderId() { return orderId; }
  public void setOrderId(String orderId) { this.orderId = orderId; }
  public int getVersion() { return version; }
  public void setVersion(int version) { this.version = version; }
  public double getGrandTotal() { return grandTotal; }
  public void setGrandTotal(double grandTotal) { this.grandTotal = grandTotal; }
  public String getSnapshotJson() { return snapshotJson; }
  public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
