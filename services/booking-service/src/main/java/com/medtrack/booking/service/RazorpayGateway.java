package com.medtrack.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RazorpayGateway {
  private final String keyId;
  private final String keySecret;
  private final ObjectMapper mapper;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();

  public RazorpayGateway(
      @Value("${medtrack.razorpay.key-id:}") String keyId,
      @Value("${medtrack.razorpay.key-secret:}") String keySecret,
      ObjectMapper mapper) {
    this.keyId = keyId == null ? "" : keyId.trim();
    this.keySecret = keySecret == null ? "" : keySecret.trim();
    this.mapper = mapper;
  }

  public boolean configured() {
    return !keyId.isBlank() && !keySecret.isBlank();
  }

  public String keyId() {
    return keyId;
  }

  public Map<String, Object> createOrder(long amountPaise, String receipt, String notesOrderId) {
    if (!configured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Razorpay is not configured. Use UPI QR or upload a payment screenshot.");
    }
    if (amountPaise < 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is too small to charge online");
    }
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("amount", amountPaise);
      body.put("currency", "INR");
      body.put("receipt", receipt == null ? "" : receipt.substring(0, Math.min(40, receipt.length())));
      Map<String, String> notes = new LinkedHashMap<>();
      notes.put("medicineOrderId", notesOrderId);
      body.put("notes", notes);
      String json = mapper.writeValueAsString(body);
      String basic =
          Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
      HttpRequest req =
          HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/orders"))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Basic " + basic)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "Razorpay order failed: " + brief(res.body()));
      }
      JsonNode node = mapper.readTree(res.body());
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("razorpayOrderId", node.path("id").asText());
      out.put("amount", node.path("amount").asLong(amountPaise));
      out.put("currency", node.path("currency").asText("INR"));
      out.put("keyId", keyId);
      return out;
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach Razorpay");
    }
  }

  public boolean verify(String orderId, String paymentId, String signature) {
    if (!configured() || orderId == null || paymentId == null || signature == null) return false;
    String payload = orderId + "|" + paymentId;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
      return expected.equalsIgnoreCase(signature.trim());
    } catch (Exception ex) {
      return false;
    }
  }

  private static String brief(String raw) {
    if (raw == null) return "";
    String s = raw.replaceAll("\\s+", " ").trim();
    return s.length() > 180 ? s.substring(0, 180) : s;
  }
}
