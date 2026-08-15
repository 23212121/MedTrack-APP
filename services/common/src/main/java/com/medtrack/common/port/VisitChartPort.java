package com.medtrack.common.port;

import java.time.Instant;
import java.util.List;

/** In-process lookup used by schedule chart (avoids HTTP to visit-service). */
public interface VisitChartPort {
  List<VisitChartItem> listForDoctorDate(String doctorId, String date);

  record VisitChartItem(
      String id,
      String patientName,
      String status,
      Instant scheduledStart,
      Instant scheduledEnd) {}
}
