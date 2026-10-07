package com.medtrack.booking.event;

/** Fired after a patient document is stored (hospital, doctor RX, lab, or booking attach). */
public record DocumentUploadedEvent(
    Long hospitalId,
    String documentId,
    String documentType,
    String patientName,
    String phone,
    String aadhaar,
    int fileCount,
    String fileNames) {}
