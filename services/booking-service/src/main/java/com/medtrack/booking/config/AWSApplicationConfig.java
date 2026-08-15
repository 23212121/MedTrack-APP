package com.medtrack.booking.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Loads all AWS S3 settings from {@code AWS_Application.properties} on the classpath.
 *
 * <p>Keys: enabled, bucket, region, access-key, secret-key, prefix, endpoint,
 * path-style-access.
 */
@Configuration
@PropertySource(value = "classpath:AWS_Application.properties", ignoreResourceNotFound = false)
public class AWSApplicationConfig {

  @Value("${aws.s3.enabled:true}")
  private boolean enabled;

  @Value("${aws.s3.bucket:}")
  private String bucket;

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

  public String getBucket() {
    return bucket == null ? "" : bucket.trim();
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
    m.put("bucket", getBucket());
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
