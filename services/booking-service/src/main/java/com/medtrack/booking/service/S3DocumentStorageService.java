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

  public String objectKey(Long hospitalId, String documentId, String storedFileName) {
    return awsDocument.buildObjectKey(hospitalId, documentId, storedFileName);
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
