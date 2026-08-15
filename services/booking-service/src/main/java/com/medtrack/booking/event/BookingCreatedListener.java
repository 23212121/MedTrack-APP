package com.medtrack.booking.event;

import com.medtrack.booking.service.BookingNotificationService;
import com.medtrack.booking.service.BookingVisitSyncService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** After booking commit: mirror visit, then SMS/email confirmation to the patient. */
@Component
public class BookingCreatedListener {
  private final BookingVisitSyncService visitSync;
  private final BookingNotificationService bookingNotify;

  public BookingCreatedListener(
      BookingVisitSyncService visitSync, BookingNotificationService bookingNotify) {
    this.visitSync = visitSync;
    this.bookingNotify = bookingNotify;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onBookingCreated(BookingCreatedEvent event) {
    visitSync.syncFromBooking(event.booking());
    bookingNotify.notifyAppointmentBooked(event.booking(), event.patientEmail());
  }
}
