package com.medtrack.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
  private static final String API = "https://api.razorpay.com/v1";

  private final String keyId;
  private final String keySecret;
  private final String webhookSecret;
  private final ObjectMapper mapper;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();

  public RazorpayGateway(
      @Value("${medtrack.razorpay.key-id:}") String keyId,
      @Value("${medtrack.razorpay.key-secret:}") String keySecret,
      @Value("${medtrack.razorpay.webhook-secret:}") String webhookSecret,
      ObjectMapper mapper) {
    this.keyId = keyId == null ? "" : keyId.trim();
    this.keySecret = keySecret == null ? "" : keySecret.trim();
    this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
    this.mapper = mapper;
  }

  public boolean configured() {
    return !keyId.isBlank() && !keySecret.isBlank();
  }

  public String keyId() {
    return keyId;
  }

  public Map<String, Object> createOrder(long amountPaise, String receipt, String notesOrderId) {
    return createOrder(amountPaise, receipt, Map.of("medicineOrderId", notesOrderId == null ? "" : notesOrderId));
  }

  public Map<String, Object> createOrder(
      long amountPaise, String receipt, Map<String, String> notes) {
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
      if (notes != null && !notes.isEmpty()) body.put("notes", notes);
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(API + "/orders"))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Basic " + basicAuth())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
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

  public Map<String, Object> createUpiQr(
      long amountPaise, String name, String paymentId, String appointmentId) {
    if (!configured()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay is not configured.");
    }
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("type", "upi_qr");
      body.put("name", name == null || name.isBlank() ? "MedTrack Clinic" : name);
      body.put("usage", "single_use");
      body.put("fixed_amount", true);
      body.put("payment_amount", amountPaise);
      body.put("description", paymentId);
      body.put("close_by", Instant.now().plus(Duration.ofMinutes(20)).getEpochSecond());
      Map<String, String> notes = new LinkedHashMap<>();
      notes.put("paymentId", paymentId);
      notes.put("appointmentId", appointmentId);
      body.put("notes", notes);
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(API + "/payments/qr_codes"))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Basic " + basicAuth())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
              .build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "Razorpay QR failed: " + brief(res.body()));
      }
      JsonNode node = mapper.readTree(res.body());
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("qrId", node.path("id").asText());
      out.put("imageUrl", node.path("image_url").asText(null));
      out.put("status", node.path("status").asText("active"));
      return out;
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not create Razorpay QR");
    }
  }

  public JsonNode fetchPayment(String razorpayPaymentId) {
    return getJson(API + "/payments/" + (razorpayPaymentId == null ? "" : razorpayPaymentId.trim()));
  }

  public JsonNode fetchOrderPayments(String razorpayOrderId) {
    if (razorpayOrderId == null || razorpayOrderId.isBlank()) return null;
    return getJson(API + "/orders/" + razorpayOrderId.trim() + "/payments");
  }

  public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
    if (webhookSecret.isBlank()) return true;
    if (rawBody == null || signatureHeader == null || signatureHeader.isBlank()) return false;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      String expected = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
      return expected.equalsIgnoreCase(signatureHeader.trim());
    } catch (Exception ex) {
      return false;
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

  private JsonNode getJson(String url) {
    if (!configured()) return null;
    try {
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(15))
              .header("Authorization", "Basic " + basicAuth())
              .GET()
              .build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() < 200 || res.statusCode() >= 300) return null;
      return mapper.readTree(res.body());
    } catch (Exception ex) {
      return null;
    }
  }

  private String basicAuth() {
    return Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
  }

  private static String brief(String raw) {
    if (raw == null) return "";
    String s = raw.replaceAll("\\s+", " ").trim();
    return s.length() > 180 ? s.substring(0, 180) : s;
  }
}
