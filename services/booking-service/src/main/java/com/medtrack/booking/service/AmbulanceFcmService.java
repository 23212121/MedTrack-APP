package com.medtrack.booking.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends an FCM data message to the driver's Flutter app when a legacy server key is configured.
 * Without a key the offer is still stored so the driver app can poll it.
 */
@Service
public class AmbulanceFcmService {
  private static final Logger log = LoggerFactory.getLogger(AmbulanceFcmService.class);
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @Value("${medtrack.fcm.server-key:}")
  private String serverKey;

  public boolean push(String fcmToken, String title, String body, String requestId, double lat, double lng) {
    if (fcmToken == null || fcmToken.isBlank()) {
      log.info("Ambulance alert stored without FCM token. Driver app should poll offers.");
      return false;
    }
    if (serverKey == null || serverKey.isBlank()) {
      log.info(
          "FCM server key not set (medtrack.fcm.server-key). Offer {} is available on the driver poll API.",
          requestId);
      return false;
    }
    String json =
        "{"
            + "\"to\":\""
            + escape(fcmToken)
            + "\","
            + "\"priority\":\"high\","
            + "\"notification\":{\"title\":\""
            + escape(title)
            + "\",\"body\":\""
            + escape(body)
            + "\"},"
            + "\"data\":{"
            + "\"type\":\"AMBULANCE_REQUEST\","
            + "\"requestId\":\""
            + escape(requestId)
            + "\","
            + "\"patientLatitude\":\""
            + lat
            + "\","
            + "\"patientLongitude\":\""
            + lng
            + "\""
            + "}}";
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("https://fcm.googleapis.com/fcm/send"))
              .timeout(Duration.ofSeconds(8))
              .header("Authorization", "key=" + serverKey.trim())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return true;
      }
      log.warn("FCM push failed status={} body={}", response.statusCode(), response.body());
    } catch (Exception ex) {
      log.warn("FCM push failed: {}", ex.getMessage());
    }
    return false;
  }

  private static String escape(String raw) {
    return raw.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
