package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "medicine_order_status_history")
public class MedicineOrderStatusHistoryEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "order_id", nullable = false)
  private String orderId;

  @Column(name = "previous_status")
  private String previousStatus;

  @Column(name = "new_status", nullable = false)
  private String newStatus;

  private String actor;
  private String note;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getOrderId() { return orderId; }
  public void setOrderId(String orderId) { this.orderId = orderId; }
  public String getPreviousStatus() { return previousStatus; }
  public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
  public String getNewStatus() { return newStatus; }
  public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
  public String getActor() { return actor; }
  public void setActor(String actor) { this.actor = actor; }
  public String getNote() { return note; }
  public void setNote(String note) { this.note = note; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
