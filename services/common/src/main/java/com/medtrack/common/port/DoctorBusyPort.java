package com.medtrack.common.port;

import java.time.LocalDate;

/** In-process check used by booking so busy / leave days cannot be reserved. */
public interface DoctorBusyPort {
  boolean isBusyOnDate(String doctorId, LocalDate date);
}
