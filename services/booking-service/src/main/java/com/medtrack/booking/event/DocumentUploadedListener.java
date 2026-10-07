package com.medtrack.booking.event;

import com.medtrack.booking.service.BookingNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** After document commit: SMS, email, and in-app notice if the patient has a booked appointment. */
@Component
public class DocumentUploadedListener {
  private final BookingNotificationService bookingNotify;

  public DocumentUploadedListener(BookingNotificationService bookingNotify) {
    this.bookingNotify = bookingNotify;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onDocumentUploaded(DocumentUploadedEvent event) {
    bookingNotify.notifyDocumentAttached(event);
  }
}
