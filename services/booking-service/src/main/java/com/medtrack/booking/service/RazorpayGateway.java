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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import jakarta.annotation.PostConstruct;

@Service
public class RazorpayGateway {
  private static final String API = "https://api.razorpay.com/v1";
  private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);

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

  @PostConstruct
  void logReady() {
    if (configured()) {
      String mode = keyId.startsWith("rzp_live_") ? "live" : "test";
      log.info("[razorpay] ready ({}) — QR and Confirm with bank can talk to Razorpay", mode);
    } else {
      log.warn("[razorpay] keys missing — set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET");
    }
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
    Map<String, String> notes = new LinkedHashMap<>();
    notes.put("paymentId", paymentId == null ? "" : paymentId);
    notes.put("appointmentId", appointmentId == null ? "" : appointmentId);
    try {
      Map<String, Object> nativeQr = createNativeUpiQr(amountPaise, name, paymentId, notes);
      if (nativeQr != null) return nativeQr;
    } catch (Exception ex) {
      log.warn("[razorpay] native UPI QR unavailable: {}", brief(ex.getMessage()));
    }
    return null;
  }

  private Map<String, Object> createNativeUpiQr(
      long amountPaise, String name, String paymentId, Map<String, String> notes) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "upi_qr");
    body.put("name", name == null || name.isBlank() ? "MedTrack Clinic" : name);
    body.put("usage", "single_use");
    body.put("fixed_amount", true);
    body.put("payment_amount", amountPaise);
    body.put("description", paymentId);
    body.put("close_by", Instant.now().plus(Duration.ofMinutes(20)).getEpochSecond());
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
      log.warn("[razorpay] native QR HTTP {}: {}", res.statusCode(), brief(res.body()));
      return null;
    }
    JsonNode node = mapper.readTree(res.body());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("qrId", node.path("id").asText());
    out.put("imageUrl", node.path("image_url").asText(null));
    out.put("status", node.path("status").asText("active"));
    out.put("kind", "upi_qr");
    return out;
  }

  /**
   * Razorpay-hosted pay page for the DB amount. Scan the QR of short_url — this account's test
   * mode does not allow native UPI QR ("UPI transactions are not enabled").
   */
  public Map<String, Object> createPaymentLinkQr(
      long amountPaise, String name, String paymentId, Map<String, String> notes) {
    if (amountPaise < 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is too small to charge online");
    }
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("amount", amountPaise);
      body.put("currency", "INR");
      body.put("accept_partial", false);
      body.put("description", name == null || name.isBlank() ? "MedTrack payment" : name);
      body.put("expire_by", Instant.now().plus(Duration.ofMinutes(20)).getEpochSecond());
      body.put("notify", Map.of("sms", false, "email", false));
      body.put("reminder_enable", false);
      if (notes != null && !notes.isEmpty()) body.put("notes", notes);
      if (paymentId != null && !paymentId.isBlank()) {
        body.put("reference_id", paymentId.length() > 40 ? paymentId.substring(0, 40) : paymentId);
      }
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(API + "/payment_links"))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Basic " + basicAuth())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
              .build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "Razorpay payment link failed: " + brief(res.body()));
      }
      JsonNode node = mapper.readTree(res.body());
      String linkId = node.path("id").asText();
      String shortUrl = node.path("short_url").asText("");
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("qrId", linkId);
      out.put("shortUrl", shortUrl);
      out.put("status", node.path("status").asText("created"));
      out.put("kind", "payment_link");
      if (!shortUrl.isBlank()) {
        out.put(
            "imageUrl",
            "https://api.qrserver.com/v1/create-qr-code/?size=280x280&data=" + urlEnc(shortUrl));
      }
      return out;
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not create Razorpay payment QR");
    }
  }

  public JsonNode fetchPaymentLink(String linkId) {
    if (linkId == null || linkId.isBlank()) return null;
    return getJson(API + "/payment_links/" + linkId.trim());
  }

  public JsonNode firstCapturedOnPaymentLink(String linkId) {
    if (linkId == null || !linkId.startsWith("plink_")) return null;
    JsonNode link = fetchPaymentLink(linkId);
    if (link == null || link.isMissingNode() || link.isNull()) return null;
    JsonNode captured = firstCapturedIn(link.path("payments"));
    if (captured != null) return captured;
    if ("paid".equalsIgnoreCase(text(link, "status"))) {
      var n = mapper.createObjectNode();
      n.put("id", firstNonBlank(text(link, "id"), linkId));
      n.put("status", "captured");
      n.put("amount", link.path("amount_paid").asLong(link.path("amount").asLong()));
      n.put("currency", link.path("currency").asText("INR"));
      n.set("notes", link.path("notes"));
      return n;
    }
    return null;
  }

  public JsonNode fetchPayment(String razorpayPaymentId) {
    return getJson(API + "/payments/" + (razorpayPaymentId == null ? "" : razorpayPaymentId.trim()));
  }

  public JsonNode fetchOrderPayments(String razorpayOrderId) {
    if (razorpayOrderId == null || razorpayOrderId.isBlank()) return null;
    return getJson(API + "/orders/" + razorpayOrderId.trim() + "/payments");
  }

  /** Payments made against a Razorpay UPI QR or payment-link QR. */
  public JsonNode fetchQrPayments(String qrId) {
    if (qrId == null || qrId.isBlank()) return null;
    if (qrId.startsWith("plink_")) {
      JsonNode link = fetchPaymentLink(qrId);
      return link == null ? null : link.path("payments");
    }
    return getJson(API + "/payments/qr_codes/" + qrId.trim() + "/payments");
  }

  public JsonNode fetchQr(String qrId) {
    if (qrId == null || qrId.isBlank()) return null;
    if (qrId.startsWith("plink_")) return fetchPaymentLink(qrId);
    return getJson(API + "/payments/qr_codes/" + qrId.trim());
  }

  public JsonNode firstCapturedIn(JsonNode collection) {
    return firstMatching(collection, true);
  }

  public JsonNode firstFailedIn(JsonNode collection) {
    return firstMatching(collection, false);
  }

  private JsonNode firstMatching(JsonNode collection, boolean success) {
    if (collection == null || collection.isMissingNode() || collection.isNull()) return null;
    JsonNode items = collection.isArray() ? collection : collection.path("items");
    if (!items.isArray()) {
      String st = text(collection, "status");
      if (success && isCaptured(st)) return collection;
      if (!success && isFailure(st)) return collection;
      return null;
    }
    for (JsonNode item : items) {
      JsonNode payment = item.has("id") ? item : item.path("payment");
      String st = text(payment, "status");
      if (success && isCaptured(st)) return payment.isMissingNode() ? item : payment;
      if (!success && isFailure(st)) return payment.isMissingNode() ? item : payment;
    }
    return null;
  }

  public static boolean isCaptured(String status) {
    if (status == null) return false;
    String s = status.trim().toUpperCase();
    return s.equals("CAPTURED")
        || s.equals("SUCCESS")
        || s.equals("PAID")
        || s.equals("COMPLETED")
        || s.equals("AUTHORIZED");
  }

  public static boolean isFailure(String status) {
    if (status == null) return false;
    String s = status.trim().toUpperCase();
    return s.contains("FAIL") || s.equals("CANCELLED") || s.equals("CANCELED");
  }

  public static String text(JsonNode node, String field) {
    if (node == null || node.isMissingNode() || node.isNull()) return "";
    JsonNode v = node.get(field);
    return v == null || v.isNull() ? "" : v.asText("").trim();
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

  private static String urlEnc(String value) {
    return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a.trim();
    return b == null ? "" : b.trim();
  }
}
