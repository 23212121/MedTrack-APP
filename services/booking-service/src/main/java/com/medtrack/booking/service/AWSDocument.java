package com.medtrack.booking.service;

import com.medtrack.booking.config.AWSApplicationConfig;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

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
  private final String prefix;
  private final AwsCredentialsProvider credentialsProvider;
  private volatile String bucket;
  private volatile String region;
  private volatile S3Client s3Client;

  public AWSDocument(AWSApplicationConfig awsConfig) {
    this.awsConfig = awsConfig;
    this.enabled = awsConfig.isEnabled();
    this.bucket = awsConfig.getBucket();
    this.region = awsConfig.getRegion();
    this.prefix = awsConfig.getPrefix();
    this.credentialsProvider =
        credentials(awsConfig.getAccessKey(), awsConfig.getSecretKey());
    this.s3Client =
        enabled
            ? connect(
                this.region,
                this.bucket,
                this.credentialsProvider,
                awsConfig.getEndpoint(),
                awsConfig.isPathStyleAccess())
            : null;
    if (this.s3Client != null) {
      boolean hasIamKeys =
          !awsConfig.getAccessKey().isBlank() && !awsConfig.getSecretKey().isBlank();
      alignRegionWithBucket();
      log.info(
          "AWSDocument connected bucket={} s3ApiTarget={} accessPointAlias={} region={} prefix={} iamKeysConfigured={}",
          awsConfig.getBucketName(),
          this.bucket,
          awsConfig.getAccessPointAlias(),
          this.region,
          this.prefix,
          hasIamKeys);
      if (!hasIamKeys) {
        log.error(
            "AWS IAM access-key/secret-key are EMPTY — uploads will fail. "
                + "Set aws.s3.access-key and aws.s3.secret-key in AWS_Application.properties "
                + "(not the -s3alias value), then rebuild and restart medtrack-app.");
      }
    } else {
      log.info("AWSDocument S3 disabled (aws.s3.enabled=false in AWS_Application.properties)");
    }
  }

  private static AwsCredentialsProvider credentials(String accessKey, String secretKey) {
    if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
      return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
    log.info("AWSDocument using DefaultCredentialsProvider (env/IAM role)");
    return DefaultCredentialsProvider.create();
  }

  /** Builds an Amazon S3 client from AWS_Application.properties values. */
  public static S3Client connect(
      String region,
      String accessKey,
      String secretKey,
      String endpoint,
      boolean pathStyleAccess) {
    return connect(region, "", credentials(accessKey, secretKey), endpoint, pathStyleAccess);
  }

  private static S3Client connect(
      String region,
      String bucketOrAlias,
      AwsCredentialsProvider credentials,
      String endpoint,
      boolean pathStyleAccess) {
    String resolvedRegion = region == null || region.isBlank() ? "ap-south-1" : region.trim();
    boolean accessPoint =
        bucketOrAlias != null && bucketOrAlias.toLowerCase().endsWith("-s3alias");

    var s3Config = S3Configuration.builder();
    if (pathStyleAccess) {
      s3Config.pathStyleAccessEnabled(true);
    }
    // Access Point aliases / ARNs must use virtual-host + ARN region
    if (accessPoint || (bucketOrAlias != null && bucketOrAlias.startsWith("arn:aws:s3:"))) {
      s3Config.pathStyleAccessEnabled(false);
      s3Config.useArnRegionEnabled(true);
    }

    var builder =
        S3Client.builder()
            .region(Region.of(resolvedRegion))
            .credentialsProvider(credentials)
            .serviceConfiguration(s3Config.build());

    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint.trim()));
    }
    // For *-s3alias: do not force endpointOverride — SDK addresses
    // {alias}.s3-accesspoint.{region}.amazonaws.com when region is correct.

    return builder.build();
  }

  /** Legacy connect without endpoint options. */
  public static S3Client connect(String region, String accessKey, String secretKey) {
    return connect(region, accessKey, secretKey, "", false);
  }

  /**
   * Fixes HTTP 301 PermanentRedirect when {@code aws.s3.region} does not match the Access Point /
   * bucket region (reads {@code x-amz-bucket-region}).
   *
   * <p>Never falls back from a working Access Point alias to a wrong display name. Prefer the real
   * bucket name from {@code aws.s3.bucket} (e.g. {@code MedTrackApp}) when no alias is set.
   */
  private void alignRegionWithBucket() {
    if (s3Client == null || bucket == null || bucket.isBlank()) {
      return;
    }
    String apiBucket = preferredApiBucket();
    if (!apiBucket.isBlank() && !apiBucket.equals(bucket)) {
      log.info("Using S3 API target {} (not {})", apiBucket, bucket);
      rebuildClient(region, apiBucket);
    }
    // HeadBucket is not allowed on Access Point ARNs/aliases and the IAM
    // boundary also denies s3:ListBucket on the raw bucket — skip that probe.
    if (bucket.startsWith("arn:aws:s3:") || bucket.toLowerCase().endsWith("-s3alias")) {
      log.info("Skipping HeadBucket for Access Point target {}", bucket);
      return;
    }
    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
    } catch (S3Exception ex) {
      String actual = bucketRegionFrom(ex);
      if (actual != null && !actual.equalsIgnoreCase(region)) {
        log.warn(
            "S3 region mismatch: configured={} actual={} — reconnecting client",
            region,
            actual);
        rebuildClient(actual, bucket);
      } else {
        // HeadBucket is often unsupported / odd for Access Point aliases — do not switch bucket
        log.warn(
            "S3 HeadBucket check failed for {} ({}). PutObject will still target this name.",
            bucket,
            ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorCode() : ex.getMessage());
      }
    } catch (Exception ex) {
      log.warn("S3 HeadBucket check skipped: {}", ex.getMessage());
    }
  }

  private void rebuildClient(String newRegion, String newBucket) {
    S3Client old = this.s3Client;
    this.region = newRegion;
    this.bucket = newBucket;
    this.s3Client =
        connect(
            newRegion,
            newBucket,
            credentialsProvider,
            awsConfig.getEndpoint(),
            awsConfig.isPathStyleAccess());
    if (old != null) {
      try {
        old.close();
      } catch (Exception ignored) {
        // ignore
      }
    }
  }

  private static String bucketRegionFrom(S3Exception ex) {
    if (ex == null || ex.awsErrorDetails() == null || ex.awsErrorDetails().sdkHttpResponse() == null) {
      return null;
    }
    return ex.awsErrorDetails()
        .sdkHttpResponse()
        .firstMatchingHeader("x-amz-bucket-region")
        .orElse(null);
  }

  /** All settings loaded from AWS_Application.properties (secret masked). */
  public Map<String, Object> getAwsDetails() {
    return awsConfig.allDetails();
  }

  /** True when S3 is enabled and the client was created. */
  public boolean isConnected() {
    return enabled && s3Client != null && bucket != null && !bucket.isBlank();
  }

  public String getBucketName() {
    return awsConfig.getBucketName();
  }

  public String getAccessPointAlias() {
    return awsConfig.getAccessPointAlias();
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

  /**
   * Builds S3 object key using Aadhaar as the document folder path.
   *
   * <p>Format: {@code {prefix}/{aadhaar}/{documentId}/{fileName}}
   * e.g. {@code patient-documents/123456789012/uuid/1_report.pdf}
   */
  public String buildObjectKey(String aadhaarNumber, String documentId, String fileName) {
    String aadhaar = aadhaarNumber == null ? "" : aadhaarNumber.replaceAll("\\D", "");
    String doc = documentId == null ? "" : documentId.replaceAll("^/+|/+$", "");
    String name = fileName == null ? "" : fileName.replaceAll("^/+", "");
    StringBuilder key = new StringBuilder();
    if (!prefix.isBlank()) {
      key.append(prefix).append('/');
    }
    if (!aadhaar.isBlank()) {
      key.append(aadhaar);
    } else {
      key.append("unknown-aadhaar");
    }
    if (!doc.isBlank()) {
      key.append('/').append(doc);
    }
    if (!name.isBlank()) {
      key.append('/').append(name);
    }
    return key.toString();
  }

  /** @deprecated Prefer {@link #buildObjectKey(String, String, String)} with Aadhaar path. */
  public String buildObjectKey(Long hospitalId, String documentId, String fileName) {
    return buildObjectKey("hospital-" + hospitalId, documentId, fileName);
  }

  /**
   * Chat attachment key. IAM permissions boundary on MedTrack-App only allows
   * {@code s3:PutObject} under {@code {prefix}/*} (patient-documents), not a top-level
   * {@code care-chat/} prefix.
   */
  public String buildChatObjectKey(String appointmentId, String fileName) {
    return buildPrefixedKey("care-chat", appointmentId, fileName);
  }

  /** Medicine-order prescription key: {@code {prefix}/medicine-orders/{orderId}/{fileName}}. */
  public String buildMedicineOrderObjectKey(String orderId, String fileName) {
    return buildPrefixedKey("medicine-orders", orderId, fileName);
  }

  /** {@code {prefix}/part/part/...} with slashes stripped from each part. */
  public String buildPrefixedKey(String... parts) {
    StringBuilder key = new StringBuilder();
    if (!prefix.isBlank()) {
      key.append(prefix);
    }
    if (parts != null) {
      for (String part : parts) {
        if (part == null || part.isBlank()) {
          continue;
        }
        String clean = part.replace('\\', '/').replaceAll("^/+|/+$", "");
        if (clean.isBlank()) {
          continue;
        }
        if (key.length() > 0) {
          key.append('/');
        }
        key.append(clean);
      }
    }
    return key.toString();
  }

  /**
   * Upload bytes to the S3 bucket.
   *
   * @param objectKey S3 object key (often from {@code file_path} + filename)
   * @return object key on success; empty if S3 is off
   * @throws IllegalStateException when S3 is enabled but PutObject fails (includes AWS error text)
   */
  public Optional<String> upload(
      String objectKey, InputStream data, long contentLength, String contentType) {
    if (!isConnected()) {
      return Optional.empty();
    }
    if (awsConfig.getAccessKey().isBlank() || awsConfig.getSecretKey().isBlank()) {
      throw new IllegalStateException(
          "IAM aws.s3.access-key / aws.s3.secret-key are empty in AWS_Application.properties. "
              + "Rebuild medtrack-app after setting keys (Access Point -s3alias is not an IAM key).");
    }
    String key = normalizeKey(objectKey);
    if (key == null || key.isBlank()) {
      throw new IllegalStateException("S3 upload key is empty");
    }
    byte[] payload;
    try {
      payload =
          contentLength >= 0 && contentLength <= Integer.MAX_VALUE
              ? data.readNBytes((int) contentLength)
              : data.readAllBytes();
    } catch (Exception ex) {
      throw new IllegalStateException("Could not read upload bytes: " + ex.getMessage(), ex);
    }
    try {
      putObject(key, payload, contentType);
      log.info("AWSDocument uploaded bucket={} region={} key={}", bucket, region, key);
      return Optional.of(key);
    } catch (S3Exception ex) {
      if (retryPutObject(key, payload, contentType, ex)) {
        return Optional.of(key);
      }
      log.error("AWSDocument upload failed bucket={} key={}: {}", bucket, key, ex.getMessage(), ex);
      throw new IllegalStateException(putObjectFailureMessage(key, ex), ex);
    } catch (Exception ex) {
      log.error("AWSDocument upload failed bucket={} key={}: {}", bucket, key, ex.getMessage(), ex);
      throw new IllegalStateException(
          "S3 PutObject failed for bucket/access-point '"
              + bucket
              + "' key '"
              + key
              + "': "
              + ex.getMessage(),
          ex);
    }
  }

  /** Real bucket name first, then alias, then Access Point ARN. */
  private String preferredApiBucket() {
    return awsConfig.getBucket();
  }

  /**
   * Retry PutObject on the other configured targets. Identity policies often
   * Allow the bucket ARN while denying the Access Point object ARN (or the
   * reverse).
   */
  private boolean retryPutObject(
      String key, byte[] payload, String contentType, S3Exception first) {
    int status = first.statusCode();
    if (status != 301 && status != 400 && status != 403 && status != 404) {
      return false;
    }
    String actual = bucketRegionFrom(first);
    if ((status == 301 || status == 400) && actual != null) {
      rebuildClient(actual, preferredApiBucket());
      try {
        putObject(key, payload, contentType);
        log.info(
            "AWSDocument uploaded after region fix bucket={} region={} key={}",
            bucket,
            region,
            key);
        return true;
      } catch (S3Exception retryEx) {
        first = retryEx;
        status = retryEx.statusCode();
      }
    }
    List<String> targets = awsConfig.getPutObjectTargets();
    for (String target : targets) {
      if (target.equals(bucket)) {
        continue;
      }
      log.warn(
          "S3 PutObject retry target={} after {} on {}", target, first.statusCode(), bucket);
      rebuildClient(actual != null ? actual : region, target);
      try {
        putObject(key, payload, contentType);
        log.info("AWSDocument uploaded bucket={} region={} key={}", bucket, region, key);
        return true;
      } catch (S3Exception retryEx) {
        log.warn("S3 PutObject retry failed target={}: {}", target, retryEx.getMessage());
        first = retryEx;
      }
    }
    return false;
  }

  /**
   * S3 still authorizes PutObject against the underlying bucket object ARN
   * ({@code arn:aws:s3:::bucket/key}), even when the SDK Bucket is an Access
   * Point ARN. A permissions boundary that only lists access-point ARNs will
   * always deny.
   */
  private String putObjectFailureMessage(String key, S3Exception ex) {
    String aws = ex.getMessage() == null ? "" : ex.getMessage();
    StringBuilder msg = new StringBuilder();
    msg.append("S3 PutObject failed for '")
        .append(bucket)
        .append("' key '")
        .append(key)
        .append("': ")
        .append(aws);
    if (aws.contains("permissions boundary")) {
      msg.append(" App config is correct (region ")
          .append(region)
          .append(", prefix patient-documents). IAM user MedTrack-App permissions boundary must Allow s3:PutObject on arn:aws:s3:::")
          .append(awsConfig.getBucketName())
          .append("/patient-documents/* — Access Point ARN alone is not enough.");
    } else if (aws.contains("identity-based policy")) {
      msg.append(" Attach an identity policy on IAM user MedTrack-App that Allows s3:PutObject on arn:aws:s3:::")
          .append(awsConfig.getBucketName())
          .append("/patient-documents/*");
    }
    return msg.toString();
  }

  private void putObject(String key, byte[] payload, String contentType) {
    PutObjectRequest.Builder req = PutObjectRequest.builder().bucket(bucket).key(key);
    if (contentType != null && !contentType.isBlank()) {
      req.contentType(contentType);
    }
    s3Client.putObject(req.build(), RequestBody.fromBytes(payload));
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
