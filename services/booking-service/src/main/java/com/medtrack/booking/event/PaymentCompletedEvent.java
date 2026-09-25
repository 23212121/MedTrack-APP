package com.medtrack.booking.event;

import java.time.Instant;

/** Published only after the payment gateway confirms and we verify the transaction. */
public record PaymentCompletedEvent(
    String eventType,
    String paymentId,
    String appointmentId,
    String patientId,
    double amount,
    String currency,
    String transactionId,
    Instant timestamp,
    String referenceType,
    String referenceId) {
  public PaymentCompletedEvent(
      String paymentId,
      String appointmentId,
      String patientId,
      double amount,
      String currency,
      String transactionId) {
    this(
        paymentId,
        appointmentId,
        patientId,
        amount,
        currency,
        transactionId,
        "APPOINTMENT",
        appointmentId);
  }

  public PaymentCompletedEvent(
      String paymentId,
      String appointmentId,
      String patientId,
      double amount,
      String currency,
      String transactionId,
      String referenceType,
      String referenceId) {
    this(
        "PAYMENT_COMPLETED",
        paymentId,
        appointmentId,
        patientId,
        amount,
        currency,
        transactionId,
        Instant.now(),
        referenceType == null || referenceType.isBlank() ? "APPOINTMENT" : referenceType,
        referenceId);
  }
}
