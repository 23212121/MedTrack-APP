package com.medtrack.booking.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Loads all AWS S3 settings from {@code AWS_Application.properties} on the classpath.
 *
 * <p>Keys: enabled, bucket, access-point-alias, region, access-key, secret-key, prefix,
 * endpoint, path-style-access.
 */
@Configuration
@PropertySource(value = "classpath:AWS_Application.properties", ignoreResourceNotFound = false)
public class AWSApplicationConfig {

  @Value("${aws.s3.enabled:true}")
  private boolean enabled;

  @Value("${aws.s3.bucket:}")
  private String bucket;

  /** S3 Access Point alias (…-s3alias). Fallback when no access-point ARN is set. */
  @Value("${aws.s3.access-point-alias:}")
  private String accessPointAlias;

  /** AWS account that owns the Access Point (required to build the AP ARN). */
  @Value("${aws.s3.account-id:}")
  private String accountId;

  /** Access Point name, e.g. medtrackdoc — not the *-s3alias value. */
  @Value("${aws.s3.access-point-name:}")
  private String accessPointName;

  /** Full Access Point ARN. When set, PutObject/GetObject use this as Bucket. */
  @Value("${aws.s3.access-point-arn:}")
  private String accessPointArn;

  @Value("${aws.s3.region:ap-south-1}")
  private String region;

  @Value("${aws.s3.access-key:}")
  private String accessKey;

  @Value("${aws.s3.secret-key:}")
  private String secretKey;

  @Value("${aws.s3.prefix:patient-documents}")
  private String prefix;

  @Value("${aws.s3.endpoint:}")
  private String endpoint;

  @Value("${aws.s3.path-style-access:false}")
  private boolean pathStyleAccess;

  public boolean isEnabled() {
    return enabled;
  }

  /** Logical bucket name (e.g. MedTrackApp). */
  public String getBucketName() {
    return bucket == null ? "" : bucket.trim();
  }

  public String getAccessPointAlias() {
    return accessPointAlias == null ? "" : accessPointAlias.trim();
  }

  public String getAccountId() {
    return accountId == null ? "" : accountId.trim();
  }

  public String getAccessPointName() {
    return accessPointName == null ? "" : accessPointName.trim();
  }

  public String getAccessPointArn() {
    if (accessPointArn != null && !accessPointArn.isBlank()) {
      return accessPointArn.trim();
    }
    String name = getAccessPointName();
    String account = getAccountId();
    if (name.isBlank() || account.isBlank()) {
      return "";
    }
    return "arn:aws:s3:" + getRegion() + ":" + account + ":accesspoint/" + name;
  }

  /**
   * Prefer the real bucket name so IAM evaluates {@code arn:aws:s3:::medtrackdoc/key}.
   * Identity policies on MedTrack-App allow that ARN; PutObject to the Access Point
   * object ARN is denied unless the user policy lists it.
   */
  public String getBucket() {
    String name = getBucketName();
    if (!name.isBlank()) {
      return name;
    }
    String alias = getAccessPointAlias();
    if (!alias.isBlank()) {
      return alias;
    }
    return getAccessPointArn();
  }

  /** PutObject targets: bucket, then alias, then Access Point ARN. */
  public java.util.List<String> getPutObjectTargets() {
    java.util.List<String> out = new java.util.ArrayList<>();
    addUnique(out, getBucketName());
    addUnique(out, getAccessPointAlias());
    addUnique(out, getAccessPointArn());
    return out;
  }

  private static void addUnique(java.util.List<String> out, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    if (!out.contains(value)) {
      out.add(value);
    }
  }

  public String getRegion() {
    return region == null || region.isBlank() ? "ap-south-1" : region.trim();
  }

  public String getAccessKey() {
    return accessKey == null ? "" : accessKey.trim();
  }

  public String getSecretKey() {
    return secretKey == null ? "" : secretKey.trim();
  }

  public String getPrefix() {
    return prefix == null ? "" : prefix.replaceAll("^/+|/+$", "");
  }

  public String getEndpoint() {
    return endpoint == null ? "" : endpoint.trim();
  }

  public boolean isPathStyleAccess() {
    return pathStyleAccess;
  }

  /** All AWS S3 details from AWS_Application.properties (secret masked). */
  public Map<String, Object> allDetails() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("enabled", enabled);
    m.put("bucket", getBucketName());
    m.put("accessPointAlias", getAccessPointAlias());
    m.put("accessPointName", getAccessPointName());
    m.put("accessPointArn", getAccessPointArn());
    m.put("accountId", getAccountId());
    m.put("s3ApiBucket", getBucket());
    m.put("region", getRegion());
    m.put("accessKey", mask(getAccessKey()));
    m.put("secretKey", mask(getSecretKey()));
    m.put("prefix", getPrefix());
    m.put("endpoint", getEndpoint());
    m.put("pathStyleAccess", pathStyleAccess);
    m.put("source", "AWS_Application.properties");
    return m;
  }

  private static String mask(String value) {
    if (value == null || value.isBlank()) return "";
    if (value.length() <= 4) return "****";
    return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
  }
}
