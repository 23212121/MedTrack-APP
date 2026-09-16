package com.medtrack.booking.service;

import java.io.InputStream;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Thin adapter over {@link AWSDocument} for patient document storage. Prefer injecting {@link
 * AWSDocument} directly for new code.
 */
@Service
public class S3DocumentStorageService {
  private final AWSDocument awsDocument;

  public S3DocumentStorageService(AWSDocument awsDocument) {
    this.awsDocument = awsDocument;
  }

  public boolean isEnabled() {
    return awsDocument.isConnected();
  }

  public String getBucket() {
    return awsDocument.getBucket();
  }

  /** Logical bucket name (MedTrackApp). */
  public String getBucketName() {
    return awsDocument.getBucketName();
  }

  /** S3 key folder/file path using Aadhaar number. */
  public String objectKey(String aadhaarNumber, String documentId, String storedFileName) {
    return awsDocument.buildObjectKey(aadhaarNumber, documentId, storedFileName);
  }

  /** @deprecated Prefer Aadhaar-based {@link #objectKey(String, String, String)}. */
  public String objectKey(Long hospitalId, String documentId, String storedFileName) {
    return awsDocument.buildObjectKey(hospitalId, documentId, storedFileName);
  }

  /** Chat files must stay under the IAM-allowed prefix (patient-documents/…). */
  public String chatObjectKey(String appointmentId, String storedFileName) {
    return awsDocument.buildChatObjectKey(appointmentId, storedFileName);
  }

  public String medicineOrderObjectKey(String orderId, String storedFileName) {
    return awsDocument.buildMedicineOrderObjectKey(orderId, storedFileName);
  }

  public Optional<String> upload(
      String objectKey, InputStream data, long contentLength, String contentType) {
    return awsDocument.upload(objectKey, data, contentLength, contentType);
  }

  public Optional<byte[]> download(String objectKeyOrPath) {
    return awsDocument.download(objectKeyOrPath);
  }

  String normalizeKey(String raw) {
    return awsDocument.normalizeKey(raw);
  }
}
