package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "medicine_orders")
public class MedicineOrderEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "order_number", nullable = false, length = 40)
  private String orderNumber;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "patient_id")
  private String patientId;

  @Column(name = "patient_name", nullable = false)
  private String patientName;

  @Column(name = "patient_phone", nullable = false)
  private String patientPhone;

  @Column(name = "doctor_id")
  private String doctorId;

  @Column(name = "doctor_name")
  private String doctorName;

  private String fulfillment = "PICKUP";

  @Column(name = "delivery_address")
  private String deliveryAddress;

  private String notes;
  private String status = "PENDING";

  @Column(name = "status_code")
  private int statusCode = 8;

  @Column(name = "assigned_store_id")
  private String assignedStoreId;

  @Column(name = "assigned_store_name")
  private String assignedStoreName;

  @Column(name = "current_amount")
  private Double currentAmount;

  @Column(name = "amount_status")
  private String amountStatus = "NOT_CALCULATED";

  @Column(name = "payment_status")
  private String paymentStatus = "UNPAID";

  @Column(name = "payment_method")
  private String paymentMethod;

  @Column(name = "razorpay_order_id")
  private String razorpayOrderId;

  @Column(name = "razorpay_payment_id")
  private String razorpayPaymentId;

  @Column(name = "booked_by", nullable = false)
  private String bookedBy;

  @Column(name = "pending_reason")
  private String pendingReason;

  @Column(name = "cancel_reason")
  private String cancelReason;

  @Column(name = "cancelled_by")
  private String cancelledBy;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "completed_by")
  private String completedBy;

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
  public String getOrderNumber() { return orderNumber; }
  public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getPatientId() { return patientId; }
  public void setPatientId(String patientId) { this.patientId = patientId; }
  public String getPatientName() { return patientName; }
  public void setPatientName(String patientName) { this.patientName = patientName; }
  public String getPatientPhone() { return patientPhone; }
  public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public String getDoctorName() { return doctorName; }
  public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
  public String getFulfillment() { return fulfillment; }
  public void setFulfillment(String fulfillment) { this.fulfillment = fulfillment; }
  public String getDeliveryAddress() { return deliveryAddress; }
  public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
  public String getNotes() { return notes; }
  public void setNotes(String notes) { this.notes = notes; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getStatusCode() { return statusCode; }
  public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
  public String getAssignedStoreId() { return assignedStoreId; }
  public void setAssignedStoreId(String assignedStoreId) { this.assignedStoreId = assignedStoreId; }
  public String getAssignedStoreName() { return assignedStoreName; }
  public void setAssignedStoreName(String assignedStoreName) { this.assignedStoreName = assignedStoreName; }
  public Double getCurrentAmount() { return currentAmount; }
  public void setCurrentAmount(Double currentAmount) { this.currentAmount = currentAmount; }
  public String getAmountStatus() { return amountStatus; }
  public void setAmountStatus(String amountStatus) { this.amountStatus = amountStatus; }
  public String getPaymentStatus() { return paymentStatus; }
  public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
  public String getPaymentMethod() { return paymentMethod; }
  public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
  public String getRazorpayOrderId() { return razorpayOrderId; }
  public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
  public String getRazorpayPaymentId() { return razorpayPaymentId; }
  public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }
  public String getBookedBy() { return bookedBy; }
  public void setBookedBy(String bookedBy) { this.bookedBy = bookedBy; }
  public String getPendingReason() { return pendingReason; }
  public void setPendingReason(String pendingReason) { this.pendingReason = pendingReason; }
  public String getCancelReason() { return cancelReason; }
  public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
  public String getCancelledBy() { return cancelledBy; }
  public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
  public Instant getCancelledAt() { return cancelledAt; }
  public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
  public Instant getCompletedAt() { return completedAt; }
  public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
  public String getCompletedBy() { return completedBy; }
  public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public String getUpdatedBy() { return updatedBy; }
  public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
