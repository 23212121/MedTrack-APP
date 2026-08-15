package com.medtrack.booking.event;

import com.medtrack.booking.domain.BookingEntity;

/** Published after a booking row is committed — triggers visit sync + patient SMS/email. */
public record BookingCreatedEvent(BookingEntity booking, String patientEmail) {
  public BookingCreatedEvent(BookingEntity booking) {
    this(booking, null);
  }
}
