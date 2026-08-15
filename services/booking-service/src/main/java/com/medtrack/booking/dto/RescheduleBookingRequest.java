package com.medtrack.booking.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

public class RescheduleBookingRequest {
  @NotNull private LocalDate appointmentDate;
  @NotNull private Instant appointmentTime;
  private String reason;

  public LocalDate getAppointmentDate() {
    return appointmentDate;
  }

  public void setAppointmentDate(LocalDate appointmentDate) {
    this.appointmentDate = appointmentDate;
  }

  public Instant getAppointmentTime() {
    return appointmentTime;
  }

  public void setAppointmentTime(Instant appointmentTime) {
    this.appointmentTime = appointmentTime;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}
