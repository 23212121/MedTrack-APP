package com.medtrack.common.dto;

import java.util.List;

public record ScheduleDayChart(
    String doctorId,
    String doctorName,
    String date,
    List<ChartBlock> blocks,
    int availableMinutes,
    int busyMinutes,
    int bookedMinutes,
    double utilizationPercent
) {
  public record ChartBlock(
      String startsAt,
      String endsAt,
      String kind,
      String label,
      String visitId,
      String status
  ) {}
}
