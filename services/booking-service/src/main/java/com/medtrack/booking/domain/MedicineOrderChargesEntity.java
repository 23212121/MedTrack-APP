package com.medtrack.booking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "medicine_order_charges")
public class MedicineOrderChargesEntity {
  @Id
  @Column(name = "order_id", length = 64)
  private String orderId;

  @Column(name = "delivery_charge")
  private double deliveryCharge;
  @Column(name = "packaging_charge")
  private double packagingCharge;
  private double tax;
  @Column(name = "other_charges")
  private double otherCharges;
  private double discount;
  @Column(name = "medicine_subtotal")
  private double medicineSubtotal;
  @Column(name = "grand_total")
  private double grandTotal;
  private boolean draft = true;
  @Column(name = "quote_version")
  private int quoteVersion = 1;

  public String getOrderId() { return orderId; }
  public void setOrderId(String orderId) { this.orderId = orderId; }
  public double getDeliveryCharge() { return deliveryCharge; }
  public void setDeliveryCharge(double deliveryCharge) { this.deliveryCharge = deliveryCharge; }
  public double getPackagingCharge() { return packagingCharge; }
  public void setPackagingCharge(double packagingCharge) { this.packagingCharge = packagingCharge; }
  public double getTax() { return tax; }
  public void setTax(double tax) { this.tax = tax; }
  public double getOtherCharges() { return otherCharges; }
  public void setOtherCharges(double otherCharges) { this.otherCharges = otherCharges; }
  public double getDiscount() { return discount; }
  public void setDiscount(double discount) { this.discount = discount; }
  public double getMedicineSubtotal() { return medicineSubtotal; }
  public void setMedicineSubtotal(double medicineSubtotal) { this.medicineSubtotal = medicineSubtotal; }
  public double getGrandTotal() { return grandTotal; }
  public void setGrandTotal(double grandTotal) { this.grandTotal = grandTotal; }
  public boolean isDraft() { return draft; }
  public void setDraft(boolean draft) { this.draft = draft; }
  public int getQuoteVersion() { return quoteVersion; }
  public void setQuoteVersion(int quoteVersion) { this.quoteVersion = quoteVersion; }
}
