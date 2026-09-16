package com.medtrack.booking.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** HMAC-signed session token for doctor / hospital logins. */
@Service
public class AuthTokenService {
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64D = Base64.getUrlDecoder();

  private final ObjectMapper mapper;
  private final String secret;
  private final long ttlHours;

  public AuthTokenService(
      ObjectMapper mapper,
      @Value("${medtrack.auth.token-secret:medtrack-local-auth-secret-change-me}") String secret,
      @Value("${medtrack.auth.token-ttl-hours:12}") long ttlHours) {
    this.mapper = mapper;
    this.secret = secret;
    this.ttlHours = ttlHours;
  }

  public String issue(Map<String, Object> claims) {
    Map<String, Object> payload = new HashMap<>(claims);
    payload.put("exp", Instant.now().plusSeconds(ttlHours * 3600).getEpochSecond());
    try {
      String body = B64.encodeToString(mapper.writeValueAsBytes(payload));
      return body + "." + B64.encodeToString(hmac(body));
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to issue auth token", ex);
    }
  }

  public Map<String, Object> verify(String token) {
    if (token == null || token.isBlank() || !token.contains(".")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session token");
    }
    int dot = token.lastIndexOf('.');
    String body = token.substring(0, dot);
    String sig = token.substring(dot + 1);
    if (!constantTimeEquals(hmac(body), decode(sig))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session token");
    }
    try {
      Map<String, Object> claims =
          mapper.readValue(decode(body), new TypeReference<Map<String, Object>>() {});
      Object exp = claims.get("exp");
      if (exp instanceof Number n && Instant.now().getEpochSecond() > n.longValue()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Sign in again.");
      }
      return claims;
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session token");
    }
  }

  private byte[] hmac(String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new IllegalStateException("HMAC failure", ex);
    }
  }

  private static byte[] decode(String value) {
    try {
      return B64D.decode(value);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session token");
    }
  }

  private static boolean constantTimeEquals(byte[] a, byte[] b) {
    if (a.length != b.length) return false;
    int r = 0;
    for (int i = 0; i < a.length; i++) r |= a[i] ^ b[i];
    return r == 0;
  }
}
