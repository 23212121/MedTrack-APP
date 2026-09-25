package com.medtrack.booking.event;

import com.medtrack.booking.service.AppointmentAppService;
import com.medtrack.booking.service.BookingNotificationService;
import com.medtrack.booking.service.EmergencyAppService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * In-process event bus (same role as RabbitMQ/Kafka PAYMENT_COMPLETED).
 * Appointment stays unpaid until this fires after gateway verification.
 */
@Component
public class PaymentCompletedListener {
  private final AppointmentAppService appointments;
  private final BookingNotificationService notifications;
  private final EmergencyAppService emergency;

  public PaymentCompletedListener(
      AppointmentAppService appointments,
      BookingNotificationService notifications,
      EmergencyAppService emergency) {
    this.appointments = appointments;
    this.notifications = notifications;
    this.emergency = emergency;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPaymentCompleted(PaymentCompletedEvent event) {
    if (event != null && "EMERGENCY_BED".equalsIgnoreCase(event.referenceType())) {
      emergency.markPaid(event.referenceId(), event.paymentId());
      return;
    }
    appointments.markPaid(event.appointmentId(), event.paymentId());
    notifications.notifyPaymentCompleted(event);
  }
}
