package com.medtrack.common.dto;

import java.util.Map;

public record NotifyRequest(
    String clinicId,
    String visitId,
    String patientId,
    String eventCode,
    String patientName,
    String patientPhone,
    String patientEmail,
    boolean smsConsent,
    boolean emailConsent,
    String doctorName,
    String clinicName,
    String scheduledTime,
    String token,
    Integer delayMinutes,
    String status,
    Double overtimeFee,
    Double totalFee,
    String feeCurrency,
    Map<String, String> extra
) {}
