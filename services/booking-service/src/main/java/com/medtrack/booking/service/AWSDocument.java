package com.medtrack.booking.service;

import com.medtrack.booking.config.AWSApplicationConfig;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * AWS S3 document helper: connect to bucket, upload, and download.
 *
 * <p>All connection details (enabled, region, bucket, keys, prefix) are loaded from {@code
 * AWS_Application.properties} via {@link AWSApplicationConfig}.
 */
@Component
public class AWSDocument {
  private static final Logger log = LoggerFactory.getLogger(AWSDocument.class);

  private final AWSApplicationConfig awsConfig;
  private final boolean enabled;
  private final String bucket;
  private final String region;
  private final String prefix;
  private final S3Client s3Client;

  public AWSDocument(AWSApplicationConfig awsConfig) {
    this.awsConfig = awsConfig;
    this.enabled = awsConfig.isEnabled();
    this.bucket = awsConfig.getBucket();
    this.region = awsConfig.getRegion();
    this.prefix = awsConfig.getPrefix();
    this.s3Client =
        enabled
            ? connect(
                this.region,
                awsConfig.getAccessKey(),
                awsConfig.getSecretKey(),
                awsConfig.getEndpoint(),
                awsConfig.isPathStyleAccess())
            : null;
    if (this.s3Client != null) {
      log.info(
          "AWSDocument connected from AWS_Application.properties bucket={} region={} prefix={}",
          this.bucket,
          this.region,
          this.prefix);
    } else {
      log.info("AWSDocument S3 disabled (aws.s3.enabled=false in AWS_Application.properties)");
    }
  }

  /** Builds an Amazon S3 client from AWS_Application.properties values. */
  public static S3Client connect(
      String region,
      String accessKey,
      String secretKey,
      String endpoint,
      boolean pathStyleAccess) {
    var builder =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint));
    }
    if (pathStyleAccess) {
      builder.serviceConfiguration(
          S3Configuration.builder().pathStyleAccessEnabled(true).build());
    }
    return builder.build();
  }

  /** Legacy connect without endpoint options. */
  public static S3Client connect(String region, String accessKey, String secretKey) {
    return connect(region, accessKey, secretKey, "", false);
  }

  /** All settings loaded from AWS_Application.properties (secret masked). */
  public Map<String, Object> getAwsDetails() {
    return awsConfig.allDetails();
  }

  /** True when S3 is enabled and the client was created. */
  public boolean isConnected() {
    return enabled && s3Client != null && bucket != null && !bucket.isBlank();
  }

  /** Lightweight check that credentials can reach the configured bucket. */
  public boolean testConnection() {
    if (!isConnected()) {
      return false;
    }
    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
      return true;
    } catch (Exception ex) {
      log.warn("AWSDocument bucket check failed bucket={}: {}", bucket, ex.getMessage());
      return false;
    }
  }

  public String getBucket() {
    return bucket;
  }

  public String getRegion() {
    return region;
  }

  public String getPrefix() {
    return prefix;
  }

  public S3Client getClient() {
    return s3Client;
  }

  /** Builds a standard object key for hospital patient documents. */
  public String buildObjectKey(Long hospitalId, String documentId, String fileName) {
    String name = fileName == null ? "" : fileName.replaceAll("^/+", "");
    StringBuilder key = new StringBuilder();
    if (!prefix.isBlank()) {
      key.append(prefix).append('/');
    }
    key.append("hospital-").append(hospitalId).append('/').append(documentId);
    if (!name.isBlank()) {
      key.append('/').append(name);
    }
    return key.toString();
  }

  /**
   * Upload bytes to the S3 bucket.
   *
   * @param objectKey S3 object key (often from {@code file_path} + filename)
   * @return object key on success; empty if S3 is off or upload fails
   */
  public Optional<String> upload(
      String objectKey, InputStream data, long contentLength, String contentType) {
    if (!isConnected()) {
      return Optional.empty();
    }
    String key = normalizeKey(objectKey);
    if (key == null || key.isBlank()) {
      log.warn("AWSDocument upload skipped: empty key");
      return Optional.empty();
    }
    try {
      PutObjectRequest.Builder req = PutObjectRequest.builder().bucket(bucket).key(key);
      if (contentType != null && !contentType.isBlank()) {
        req.contentType(contentType);
      }
      s3Client.putObject(req.build(), RequestBody.fromInputStream(data, contentLength));
      log.debug("AWSDocument uploaded key={}", key);
      return Optional.of(key);
    } catch (Exception ex) {
      log.warn("AWSDocument upload failed key={}: {}", key, ex.getMessage());
      return Optional.empty();
    }
  }

  /** Upload a local file to S3. */
  public Optional<String> uploadFile(String objectKey, Path localFile, String contentType) {
    if (localFile == null || !Files.isRegularFile(localFile)) {
      return Optional.empty();
    }
    try {
      byte[] bytes = Files.readAllBytes(localFile);
      return upload(objectKey, new ByteArrayInputStream(bytes), bytes.length, contentType);
    } catch (Exception ex) {
      log.warn("AWSDocument uploadFile failed key={}: {}", objectKey, ex.getMessage());
      return Optional.empty();
    }
  }

  /** Upload raw bytes to S3. */
  public Optional<String> uploadBytes(String objectKey, byte[] bytes, String contentType) {
    if (bytes == null) {
      return Optional.empty();
    }
    return upload(objectKey, new ByteArrayInputStream(bytes), bytes.length, contentType);
  }

  /**
   * Download an object from S3 using a key or path from {@code svc.documents.file_path}.
   *
   * @param filePathOrKey plain key, {@code s3://bucket/key}, or HTTPS S3 URL
   * @return file bytes if found
   */
  public Optional<byte[]> download(String filePathOrKey) {
    if (!isConnected()) {
      return Optional.empty();
    }
    String key = normalizeKey(filePathOrKey);
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    try {
      GetObjectRequest req = GetObjectRequest.builder().bucket(bucket).key(key).build();
      byte[] data = s3Client.getObjectAsBytes(req).asByteArray();
      log.debug("AWSDocument downloaded key={} bytes={}", key, data.length);
      return Optional.of(data);
    } catch (Exception ex) {
      log.warn("AWSDocument download failed key={}: {}", key, ex.getMessage());
      return Optional.empty();
    }
  }

  /** Download and write to a local path. Returns the path on success. */
  public Optional<Path> downloadToFile(String filePathOrKey, Path targetFile) {
    Optional<byte[]> data = download(filePathOrKey);
    if (data.isEmpty()) {
      return Optional.empty();
    }
    try {
      if (targetFile.getParent() != null) {
        Files.createDirectories(targetFile.getParent());
      }
      Files.write(targetFile, data.get());
      return Optional.of(targetFile);
    } catch (Exception ex) {
      log.warn("AWSDocument downloadToFile failed: {}", ex.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Turns stored {@code file_path} / URLs into an S3 object key.
   *
   * <p>Accepts: plain key, {@code s3://bucket/key}, HTTPS S3 URLs.
   */
  public String normalizeKey(String raw) {
    if (raw == null) return null;
    String v = raw.trim();
    if (v.isBlank()) return null;

    if (v.regionMatches(true, 0, "s3://", 0, 5)) {
      String without = v.substring(5);
      int slash = without.indexOf('/');
      if (slash < 0) return null;
      return without.substring(slash + 1).replaceAll("^/+", "");
    }

    if (v.regionMatches(true, 0, "http://", 0, 7) || v.regionMatches(true, 0, "https://", 0, 8)) {
      try {
        URI uri = URI.create(v);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("^/+", "");
        if (host.startsWith("s3.") || host.startsWith("s3-")) {
          int slash = path.indexOf('/');
          if (slash < 0) return null;
          return path.substring(slash + 1);
        }
        return path;
      } catch (Exception ex) {
        log.debug("AWSDocument could not parse URL: {}", v);
        return null;
      }
    }

    // Local Windows/Unix paths are not S3 keys
    if (v.matches("^[A-Za-z]:\\\\.*") || v.matches("^[A-Za-z]:/.*")) {
      return null;
    }
    if (v.startsWith("/") && (v.contains("uploads") || v.contains("\\"))) {
      return null;
    }

    return v.replace('\\', '/').replaceAll("^/+", "");
  }

  @PreDestroy
  public void close() {
    if (s3Client != null) {
      try {
        s3Client.close();
      } catch (Exception ignored) {
        // ignore
      }
    }
  }
}
