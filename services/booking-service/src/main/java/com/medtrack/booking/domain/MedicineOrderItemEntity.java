package com.medtrack.booking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "medicine_order_items")
public class MedicineOrderItemEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "order_id", nullable = false)
  private String orderId;

  @Column(name = "prescribed_name", nullable = false)
  private String prescribedName;

  @Column(name = "medicine_name", nullable = false)
  private String medicineName;

  private double quantity;
  @Column(name = "unit_price")
  private double unitPrice;
  @Column(name = "line_total")
  private double lineTotal;
  private String availability = "AVAILABLE";
  @Column(name = "substitute_name")
  private String substituteName;
  @Column(name = "substitute_reason")
  private String substituteReason;
  @Column(name = "sort_order")
  private int sortOrder;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getOrderId() { return orderId; }
  public void setOrderId(String orderId) { this.orderId = orderId; }
  public String getPrescribedName() { return prescribedName; }
  public void setPrescribedName(String prescribedName) { this.prescribedName = prescribedName; }
  public String getMedicineName() { return medicineName; }
  public void setMedicineName(String medicineName) { this.medicineName = medicineName; }
  public double getQuantity() { return quantity; }
  public void setQuantity(double quantity) { this.quantity = quantity; }
  public double getUnitPrice() { return unitPrice; }
  public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
  public double getLineTotal() { return lineTotal; }
  public void setLineTotal(double lineTotal) { this.lineTotal = lineTotal; }
  public String getAvailability() { return availability; }
  public void setAvailability(String availability) { this.availability = availability; }
  public String getSubstituteName() { return substituteName; }
  public void setSubstituteName(String substituteName) { this.substituteName = substituteName; }
  public String getSubstituteReason() { return substituteReason; }
  public void setSubstituteReason(String substituteReason) { this.substituteReason = substituteReason; }
  public int getSortOrder() { return sortOrder; }
  public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
