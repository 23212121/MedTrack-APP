package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class PaymentEntity {
  @Id
  @Column(name = "payment_id", length = 40)
  private String paymentId;

  @Column(name = "appointment_id", length = 36)
  private String appointmentId;

  /** APPOINTMENT | EMERGENCY_BED */
  @Column(name = "reference_type", length = 30)
  private String referenceType = "APPOINTMENT";

  @Column(name = "reference_id", length = 64)
  private String referenceId;

  @Column(name = "patient_id", nullable = false, length = 64)
  private String patientId;

  @Column(nullable = false)
  private Double amount;

  @Column(length = 10)
  private String currency = "INR";

  @Column(nullable = false, length = 30)
  private String status = "CREATED";

  @Column(name = "gateway_transaction_id", length = 150, unique = true)
  private String gatewayTransactionId;

  @Column(name = "gateway_name", length = 50)
  private String gatewayName = "RAZORPAY";

  @Column(name = "gateway_order_id", length = 80)
  private String gatewayOrderId;

  @Column(name = "gateway_qr_id", length = 80)
  private String gatewayQrId;

  @Column(name = "qr_image_url", length = 1000)
  private String qrImageUrl;

  @Column(name = "upi_uri", length = 500)
  private String upiUri;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at")
  private Instant updatedAt;

  public String getPaymentId() { return paymentId; }
  public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
  public String getAppointmentId() { return appointmentId; }
  public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }
  public String getReferenceType() { return referenceType; }
  public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
  public String getReferenceId() { return referenceId; }
  public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
  public String getPatientId() { return patientId; }
  public void setPatientId(String patientId) { this.patientId = patientId; }
  public Double getAmount() { return amount; }
  public void setAmount(Double amount) { this.amount = amount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getGatewayTransactionId() { return gatewayTransactionId; }
  public void setGatewayTransactionId(String gatewayTransactionId) { this.gatewayTransactionId = gatewayTransactionId; }
  public String getGatewayName() { return gatewayName; }
  public void setGatewayName(String gatewayName) { this.gatewayName = gatewayName; }
  public String getGatewayOrderId() { return gatewayOrderId; }
  public void setGatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; }
  public String getGatewayQrId() { return gatewayQrId; }
  public void setGatewayQrId(String gatewayQrId) { this.gatewayQrId = gatewayQrId; }
  public String getQrImageUrl() { return qrImageUrl; }
  public void setQrImageUrl(String qrImageUrl) { this.qrImageUrl = qrImageUrl; }
  public String getUpiUri() { return upiUri; }
  public void setUpiUri(String upiUri) { this.upiUri = upiUri; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
