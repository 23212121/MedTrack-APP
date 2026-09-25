package com.medtrack.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.domain.PaymentEntity;
import com.medtrack.booking.event.PaymentCompletedEvent;
import com.medtrack.booking.repo.AppointmentRepository;
import com.medtrack.booking.repo.PaymentRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentAppService {
  public static final String CREATED = "CREATED";
  public static final String PAYMENT_PENDING = "PAYMENT_PENDING";
  public static final String COMPLETED = "COMPLETED";
  public static final String FAILED = "FAILED";
  public static final String CANCELLED = "CANCELLED";
  public static final String EXPIRED = "EXPIRED";
  public static final String REFUNDED = "REFUNDED";

  private static final Duration QR_TTL = Duration.ofMinutes(20);
  private static final Logger log = LoggerFactory.getLogger(PaymentAppService.class);

  private final PaymentRepository paymentRepo;
  private final AppointmentRepository appointmentRepo;
  private final AppointmentEnrichmentService enrichment;
  private final RazorpayGateway razorpay;
  private final ApplicationEventPublisher events;
  private final ObjectMapper mapper;
  private final String defaultUpiId;

  public PaymentAppService(
      PaymentRepository paymentRepo,
      AppointmentRepository appointmentRepo,
      AppointmentEnrichmentService enrichment,
      RazorpayGateway razorpay,
      ApplicationEventPublisher events,
      ObjectMapper mapper,
      @Value("${medtrack.payment.upi-id:medtrackclinic@upi}") String defaultUpiId) {
    this.paymentRepo = paymentRepo;
    this.appointmentRepo = appointmentRepo;
    this.enrichment = enrichment;
    this.razorpay = razorpay;
    this.events = events;
    this.mapper = mapper;
    this.defaultUpiId = defaultUpiId == null || defaultUpiId.isBlank()
        ? "medtrackclinic@upi"
        : defaultUpiId.trim();
  }

  /**
   * QR scan only starts the payment. Status is CREATED then PAYMENT_PENDING — never COMPLETED here.
   */
  @Transactional
  public Map<String, Object> create(Map<String, Object> body) {
    String referenceType = firstNonBlank(str(body, "referenceType"), "APPOINTMENT");
    if ("EMERGENCY_BED".equalsIgnoreCase(referenceType)) {
      return createEmergency(body);
    }
    String appointmentId = firstNonBlank(str(body, "appointmentId"), str(body, "referenceId"));
    if (appointmentId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appointmentId is required");
    }
    AppointmentEntity appt =
        appointmentRepo
            .findById(appointmentId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

    PaymentEntity existingCompleted =
        paymentRepo.findByAppointmentIdOrderByCreatedAtDesc(appointmentId).stream()
            .filter(p -> COMPLETED.equalsIgnoreCase(p.getStatus()))
            .findFirst()
            .orElse(null);
    if (existingCompleted != null || "PAID".equalsIgnoreCase(nullTo(appt.getPaymentStatus()))) {
      PaymentEntity paid = existingCompleted != null ? existingCompleted : latest(appointmentId);
      return toMap(paid, "Payment already completed");
    }

    PaymentEntity reusable =
        paymentRepo.findByAppointmentIdOrderByCreatedAtDesc(appointmentId).stream()
            .filter(p -> CREATED.equalsIgnoreCase(p.getStatus())
                || PAYMENT_PENDING.equalsIgnoreCase(p.getStatus()))
            .findFirst()
            .orElse(null);
    if (reusable != null && !expired(reusable)) {
      if (reusable.getQrImageUrl() == null || reusable.getQrImageUrl().isBlank()) {
        attachGatewayInstruments(
            reusable, enrichment.hospitalName(appt.getHospitalId()), appointmentId);
        reusable.setStatus(PAYMENT_PENDING);
        reusable.setUpdatedAt(Instant.now());
        reusable = paymentRepo.save(reusable);
      }
      markAppointmentPending(appt, reusable.getPaymentId());
      return toMap(reusable, "Scan the QR. Payment stays pending until the bank confirms.");
    }
    if (reusable != null) {
      reusable.setStatus(EXPIRED);
      reusable.setUpdatedAt(Instant.now());
      paymentRepo.save(reusable);
    }

    String patientId = firstNonBlank(str(body, "patientId"), appt.getPatientId());
    double amount = amountFrom(body, appt);
    if (amount < 1) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Consultation fee is too small to charge online");
    }

    PaymentEntity row = new PaymentEntity();
    row.setPaymentId(nextPaymentId());
    row.setAppointmentId(appointmentId);
    row.setReferenceType("APPOINTMENT");
    row.setReferenceId(appointmentId);
    row.setPatientId(patientId);
    row.setAmount(amount);
    row.setCurrency("INR");
    row.setStatus(CREATED);
    row.setGatewayName(razorpay.configured() ? "RAZORPAY" : "UPI");
    row.setCreatedAt(Instant.now());
    row = paymentRepo.save(row);

    attachGatewayInstruments(row, enrichment.hospitalName(appt.getHospitalId()), appointmentId);
    row.setStatus(PAYMENT_PENDING);
    row.setUpdatedAt(Instant.now());
    row = paymentRepo.save(row);
    markAppointmentPending(appt, row.getPaymentId());
    return toMap(row, "Scan the QR. Payment stays pending until the bank confirms.");
  }

  @Transactional
  public Map<String, Object> createEmergency(Map<String, Object> body) {
    String bookingId = firstNonBlank(str(body, "referenceId"), str(body, "bookingId"));
    if (bookingId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Emergency booking id is required");
    }
    PaymentEntity existingCompleted =
        paymentRepo
            .findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc("EMERGENCY_BED", bookingId)
            .stream()
            .filter(p -> COMPLETED.equalsIgnoreCase(p.getStatus()))
            .findFirst()
            .orElse(null);
    if (existingCompleted != null) {
      return toMap(existingCompleted, "Payment already completed");
    }
    PaymentEntity reusable =
        paymentRepo
            .findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc("EMERGENCY_BED", bookingId)
            .stream()
            .filter(
                p ->
                    CREATED.equalsIgnoreCase(p.getStatus())
                        || PAYMENT_PENDING.equalsIgnoreCase(p.getStatus()))
            .findFirst()
            .orElse(null);
    if (reusable != null && !expired(reusable)) {
      if (reusable.getQrImageUrl() == null || reusable.getQrImageUrl().isBlank()) {
        attachGatewayInstruments(
            reusable, firstNonBlank(str(body, "hospitalName"), "MedTrack Emergency"), bookingId);
        reusable.setStatus(PAYMENT_PENDING);
        reusable.setUpdatedAt(Instant.now());
        reusable = paymentRepo.save(reusable);
      }
      return toMap(reusable, "Scan the QR. Payment stays pending until the bank confirms.");
    }
    if (reusable != null) {
      reusable.setStatus(EXPIRED);
      reusable.setUpdatedAt(Instant.now());
      paymentRepo.save(reusable);
    }

    double amount = amountFrom(body, null);
    if (amount < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bed fee is too small to charge online");
    }
    String patientId = firstNonBlank(str(body, "patientId"), "emergency-patient");

    PaymentEntity row = new PaymentEntity();
    row.setPaymentId(nextPaymentId());
    row.setAppointmentId(null);
    row.setReferenceType("EMERGENCY_BED");
    row.setReferenceId(bookingId);
    row.setPatientId(patientId);
    row.setAmount(amount);
    row.setCurrency("INR");
    row.setStatus(CREATED);
    row.setGatewayName(razorpay.configured() ? "RAZORPAY" : "UPI");
    row.setCreatedAt(Instant.now());
    row = paymentRepo.save(row);

    attachGatewayInstruments(
        row, firstNonBlank(str(body, "hospitalName"), "MedTrack Emergency"), bookingId);
    row.setStatus(PAYMENT_PENDING);
    row.setUpdatedAt(Instant.now());
    row = paymentRepo.save(row);
    return toMap(row, "Scan the QR. Payment stays pending until the bank confirms.");
  }

  @Transactional
  public Map<String, Object> get(String paymentId) {
    PaymentEntity row = require(paymentId);
    expireIfNeeded(row);
    return toMap(row, null);
  }

  @Transactional
  public Map<String, Object> getByAppointment(String appointmentId) {
    PaymentEntity row =
        paymentRepo.findByAppointmentIdOrderByCreatedAtDesc(appointmentId).stream()
            .findFirst()
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No payment for appointment"));
    expireIfNeeded(row);
    return toMap(row, null);
  }

  @Transactional
  public Map<String, Object> cancel(String paymentId) {
    PaymentEntity row = require(paymentId);
    if (COMPLETED.equalsIgnoreCase(row.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid payments cannot be cancelled");
    }
    if (PAYMENT_PENDING.equalsIgnoreCase(row.getStatus())
        || CREATED.equalsIgnoreCase(row.getStatus())) {
      row.setStatus(CANCELLED);
      row.setUpdatedAt(Instant.now());
      paymentRepo.save(row);
    }
    return toMap(row, "Payment cancelled");
  }

  /**
   * Gateway webhook. Never trust the payload alone — verify with the provider, then COMPLETED.
   */
  @Transactional
  public Map<String, Object> handleWebhook(String rawBody, String signatureHeader) {
    if (rawBody == null || rawBody.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty webhook");
    }
    if (!razorpay.verifyWebhookSignature(rawBody, signatureHeader)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
    }
    JsonNode root;
    try {
      root = mapper.readTree(rawBody);
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook JSON");
    }
    String event = text(root, "event");
    JsonNode paymentNode = paymentNodeFromWebhook(root);
    String txnId =
        firstNonBlank(
            text(root, "transactionId"),
            text(paymentNode, "id"),
            text(root, "razorpay_payment_id"));
    String paymentId =
        firstNonBlank(
            text(root, "paymentId"),
            text(paymentNode.path("notes"), "paymentId"),
            text(root.path("notes"), "paymentId"));
    String orderId =
        firstNonBlank(text(paymentNode, "order_id"), text(root, "orderId"), text(root, "order_id"));
    String qrId =
        firstNonBlank(
            text(root.path("payload").path("qr_code").path("entity"), "id"),
            text(paymentNode, "qr_code_id"));
    String statusHint =
        firstNonBlank(text(root, "status"), text(paymentNode, "status"), event).toUpperCase();

    if (isFailure(statusHint) || "payment.failed".equalsIgnoreCase(event)) {
      PaymentEntity row = findPayment(paymentId, txnId, orderId, qrId);
      if (row != null && !COMPLETED.equalsIgnoreCase(row.getStatus())) {
        row.setStatus(FAILED);
        row.setUpdatedAt(Instant.now());
        if (!txnId.isBlank()) row.setGatewayTransactionId(txnId);
        paymentRepo.save(row);
      }
      return Map.of("ok", true, "status", FAILED, "idempotent", false);
    }

    if (txnId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "transactionId is required");
    }
    if (!razorpay.configured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Cannot verify webhook: payment gateway is not configured");
    }
    JsonNode verified = razorpay.fetchPayment(txnId);
    if (verified == null || verified.isMissingNode() || verified.isNull()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Could not verify transaction with payment gateway");
    }
    return completeFromVerified(verified, paymentId, orderId, qrId);
  }

  /**
   * Local/dev fallback when the gateway cannot reach our webhook. Still verifies with Razorpay.
   */
  @Transactional
  public Map<String, Object> verifyWithGateway(String paymentId) {
    PaymentEntity row = require(paymentId);
    expireIfNeeded(row);
    if (COMPLETED.equalsIgnoreCase(row.getStatus())) {
      return toMap(row, "Already completed");
    }
    if (!PAYMENT_PENDING.equalsIgnoreCase(row.getStatus())
        && !CREATED.equalsIgnoreCase(row.getStatus())) {
      return toMap(row, null);
    }
    if (!razorpay.configured()) {
      return toMap(row, "Waiting for payment gateway confirmation");
    }
    JsonNode payments = razorpay.fetchOrderPayments(row.getGatewayOrderId());
    if (payments != null && payments.path("items").isArray()) {
      for (JsonNode item : payments.path("items")) {
        if (isCaptured(text(item, "status"))) {
          return completeFromVerified(
              item, row.getPaymentId(), row.getGatewayOrderId(), row.getGatewayQrId());
        }
      }
    }
    if (row.getGatewayTransactionId() != null && !row.getGatewayTransactionId().isBlank()) {
      JsonNode fetched = razorpay.fetchPayment(row.getGatewayTransactionId());
      if (fetched != null && isCaptured(text(fetched, "status"))) {
        return completeFromVerified(
            fetched, row.getPaymentId(), row.getGatewayOrderId(), row.getGatewayQrId());
      }
    }
    return toMap(row, "Waiting for payment gateway confirmation");
  }

  private Map<String, Object> completeFromVerified(
      JsonNode verified, String paymentIdHint, String orderIdHint, String qrIdHint) {
    String txnId = text(verified, "id");
    if (txnId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verified payment has no id");
    }
    if (!isCaptured(text(verified, "status"))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Gateway status is not SUCCESS/captured");
    }

    PaymentEntity already = paymentRepo.findByGatewayTransactionId(txnId).orElse(null);
    if (already != null && COMPLETED.equalsIgnoreCase(already.getStatus())) {
      return toMap(already, "Duplicate webhook ignored");
    }

    String paymentId =
        firstNonBlank(
            paymentIdHint,
            text(verified.path("notes"), "paymentId"),
            already == null ? "" : already.getPaymentId());
    String orderId = firstNonBlank(orderIdHint, text(verified, "order_id"));
    String qrId = firstNonBlank(qrIdHint, text(verified, "qr_code_id"));
    PaymentEntity row = findPayment(paymentId, txnId, orderId, qrId);
    if (row == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found for webhook");
    }

    PaymentEntity locked = paymentRepo.lockById(row.getPaymentId()).orElse(row);
    if (COMPLETED.equalsIgnoreCase(locked.getStatus())) {
      return toMap(locked, "Duplicate webhook ignored");
    }
    if (txnId.equals(locked.getGatewayTransactionId())
        && COMPLETED.equalsIgnoreCase(locked.getStatus())) {
      return toMap(locked, "Duplicate webhook ignored");
    }

    validateVerified(locked, verified, txnId);

    locked.setGatewayTransactionId(txnId);
    if (!orderId.isBlank()) locked.setGatewayOrderId(orderId);
    if (!qrId.isBlank()) locked.setGatewayQrId(qrId);
    locked.setStatus(COMPLETED);
    locked.setUpdatedAt(Instant.now());
    try {
      locked = paymentRepo.saveAndFlush(locked);
    } catch (DataIntegrityViolationException dup) {
      PaymentEntity winner = paymentRepo.findByGatewayTransactionId(txnId).orElse(locked);
      return toMap(winner, "Duplicate webhook ignored");
    }

    expireSiblingPendings(locked);
    events.publishEvent(
        new PaymentCompletedEvent(
            locked.getPaymentId(),
            locked.getAppointmentId(),
            locked.getPatientId(),
            locked.getAmount() == null ? 0 : locked.getAmount(),
            locked.getCurrency(),
            txnId,
            firstNonBlank(locked.getReferenceType(), "APPOINTMENT"),
            firstNonBlank(locked.getReferenceId(), locked.getAppointmentId())));
    log.info(
        "[payment] COMPLETED paymentId={} appointmentId={} txn={}",
        locked.getPaymentId(),
        locked.getAppointmentId(),
        txnId);
    return toMap(locked, "Payment completed");
  }

  private void validateVerified(PaymentEntity row, JsonNode verified, String txnId) {
    if (txnId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing gateway transaction id");
    }
    String currency = firstNonBlank(text(verified, "currency"), "INR");
    if (!currency.equalsIgnoreCase(nullTo(row.getCurrency(), "INR"))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Currency mismatch");
    }
    long expectedPaise = Math.round((row.getAmount() == null ? 0 : row.getAmount()) * 100);
    long actualPaise = amountPaise(verified, expectedPaise);
    if (actualPaise > 0 && Math.abs(expectedPaise - actualPaise) > 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Amount mismatch");
    }
    String notePaymentId = text(verified.path("notes"), "paymentId");
    if (!notePaymentId.isBlank() && !notePaymentId.equalsIgnoreCase(row.getPaymentId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment ID mismatch");
    }
    String noteAppt = text(verified.path("notes"), "appointmentId");
    if (!noteAppt.isBlank()) {
      boolean matchesAppointment = noteAppt.equalsIgnoreCase(nullTo(row.getAppointmentId()));
      boolean matchesReference = noteAppt.equalsIgnoreCase(nullTo(row.getReferenceId()));
      if (!matchesAppointment && !matchesReference) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Appointment ID mismatch");
      }
    }
  }

  private void attachGatewayInstruments(PaymentEntity row, String payeeName, String noteRefId) {
    long paise = Math.round((row.getAmount() == null ? 0 : row.getAmount()) * 100);
    String clinic = firstNonBlank(payeeName, "MedTrack Clinic");
    Map<String, String> notes = new LinkedHashMap<>();
    notes.put("paymentId", row.getPaymentId());
    notes.put("appointmentId", firstNonBlank(row.getAppointmentId(), noteRefId));
    notes.put("referenceType", firstNonBlank(row.getReferenceType(), "APPOINTMENT"));
    notes.put("referenceId", firstNonBlank(row.getReferenceId(), noteRefId));
    if (razorpay.configured() && paise >= 100) {
      try {
        Map<String, Object> order =
            razorpay.createOrder(paise, row.getPaymentId(), notes);
        row.setGatewayOrderId(String.valueOf(order.getOrDefault("razorpayOrderId", "")));
        row.setGatewayName("RAZORPAY");
      } catch (Exception ex) {
        log.warn("[payment] Razorpay order failed: {}", ex.getMessage());
      }
      try {
        Map<String, Object> qr =
            razorpay.createUpiQr(
                paise,
                clinic,
                row.getPaymentId(),
                firstNonBlank(row.getAppointmentId(), noteRefId));
        row.setGatewayQrId(String.valueOf(qr.getOrDefault("qrId", "")));
        String image = String.valueOf(qr.getOrDefault("imageUrl", ""));
        if (image != null && !image.isBlank() && !"null".equals(image)) {
          row.setQrImageUrl(image);
        }
        row.setGatewayName("RAZORPAY");
      } catch (Exception ex) {
        log.warn("[payment] Razorpay QR failed, using UPI fallback: {}", ex.getMessage());
      }
    }
    String uri = upiUri(row, clinic);
    row.setUpiUri(uri);
    if (row.getQrImageUrl() == null || row.getQrImageUrl().isBlank()) {
      row.setQrImageUrl(
          "https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=" + urlEnc(uri));
    }
  }

  private String upiUri(PaymentEntity row, String payeeName) {
    double amt = row.getAmount() == null ? 0 : row.getAmount();
    return "upi://pay?pa="
        + urlEnc(defaultUpiId)
        + "&pn="
        + urlEnc(payeeName)
        + "&am="
        + String.format(java.util.Locale.US, "%.2f", amt)
        + "&cu=INR&tn="
        + urlEnc(row.getPaymentId());
  }

  private void markAppointmentPending(AppointmentEntity appt, String paymentId) {
    if ("PAID".equalsIgnoreCase(nullTo(appt.getPaymentStatus()))) return;
    appt.setPaymentStatus("PAYMENT_PENDING");
    appt.setPaymentId(paymentId);
    appointmentRepo.save(appt);
  }

  private void expireSiblingPendings(PaymentEntity completed) {
    List<PaymentEntity> siblings;
    if (!blank(completed.getAppointmentId())) {
      siblings = paymentRepo.findByAppointmentIdOrderByCreatedAtDesc(completed.getAppointmentId());
    } else if (!blank(completed.getReferenceId())) {
      siblings =
          paymentRepo.findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
              firstNonBlank(completed.getReferenceType(), "EMERGENCY_BED"),
              completed.getReferenceId());
    } else {
      return;
    }
    for (PaymentEntity other : siblings) {
      if (completed.getPaymentId().equals(other.getPaymentId())) continue;
      if (CREATED.equalsIgnoreCase(other.getStatus())
          || PAYMENT_PENDING.equalsIgnoreCase(other.getStatus())) {
        other.setStatus(EXPIRED);
        other.setUpdatedAt(Instant.now());
        paymentRepo.save(other);
      }
    }
  }

  private void expireIfNeeded(PaymentEntity row) {
    if ((CREATED.equalsIgnoreCase(row.getStatus())
            || PAYMENT_PENDING.equalsIgnoreCase(row.getStatus()))
        && expired(row)) {
      row.setStatus(EXPIRED);
      row.setUpdatedAt(Instant.now());
      paymentRepo.save(row);
    }
  }

  private boolean expired(PaymentEntity row) {
    Instant created = row.getCreatedAt() == null ? Instant.now() : row.getCreatedAt();
    return Instant.now().isAfter(created.plus(QR_TTL));
  }

  private PaymentEntity findPayment(String paymentId, String txnId, String orderId, String qrId) {
    if (!blank(paymentId)) {
      PaymentEntity row = paymentRepo.findById(paymentId).orElse(null);
      if (row != null) return row;
    }
    if (!blank(txnId)) {
      PaymentEntity row = paymentRepo.findByGatewayTransactionId(txnId).orElse(null);
      if (row != null) return row;
    }
    if (!blank(orderId)) {
      PaymentEntity row = paymentRepo.findByGatewayOrderId(orderId).orElse(null);
      if (row != null) return row;
    }
    if (!blank(qrId)) {
      return paymentRepo.findByGatewayQrId(qrId).orElse(null);
    }
    return null;
  }

  private PaymentEntity require(String paymentId) {
    return paymentRepo
        .findById(paymentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
  }

  private PaymentEntity latest(String appointmentId) {
    return paymentRepo.findByAppointmentIdOrderByCreatedAtDesc(appointmentId).stream()
        .findFirst()
        .orElse(null);
  }

  private Map<String, Object> toMap(PaymentEntity row, String message) {
    if (row == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("paymentId", row.getPaymentId());
    m.put("appointmentId", row.getAppointmentId());
    m.put("referenceType", firstNonBlank(row.getReferenceType(), "APPOINTMENT"));
    m.put("referenceId", firstNonBlank(row.getReferenceId(), row.getAppointmentId()));
    m.put("patientId", row.getPatientId());
    m.put("amount", row.getAmount());
    m.put("currency", row.getCurrency());
    m.put("status", row.getStatus());
    m.put("gatewayTransactionId", row.getGatewayTransactionId());
    m.put("gatewayName", row.getGatewayName());
    m.put("gatewayOrderId", row.getGatewayOrderId());
    m.put("qrImageUrl", row.getQrImageUrl());
    m.put("upiUri", row.getUpiUri());
    m.put("upiId", defaultUpiId);
    m.put("razorpayEnabled", razorpay.configured());
    m.put("createdAt", row.getCreatedAt() == null ? null : row.getCreatedAt().toString());
    m.put("updatedAt", row.getUpdatedAt() == null ? null : row.getUpdatedAt().toString());
    m.put("upiApps", upiApps(row.getUpiUri()));
    if (message != null) m.put("message", message);
    return m;
  }

  private List<Map<String, String>> upiApps(String uri) {
    List<Map<String, String>> apps = new ArrayList<>();
    String q = "";
    if (uri != null && uri.contains("?")) {
      q = uri.substring(uri.indexOf('?') + 1);
    }
    apps.add(upiApp("gpay", "Google Pay", "tez://upi/pay?" + q));
    apps.add(upiApp("phonepe", "PhonePe", "phonepe://pay?" + q));
    apps.add(upiApp("paytm", "Paytm", "paytmmp://pay?" + q));
    apps.add(upiApp("bhim", "BHIM", "bhim://pay?" + q));
    apps.add(upiApp("upi", "Other UPI apps", uri == null ? "" : uri));
    return apps;
  }

  private static Map<String, String> upiApp(String id, String name, String uri) {
    Map<String, String> row = new LinkedHashMap<>();
    row.put("id", id);
    row.put("name", name);
    row.put("uri", uri);
    return row;
  }

  private double amountFrom(Map<String, Object> body, AppointmentEntity appt) {
    Object raw = body == null ? null : body.get("amount");
    if (raw instanceof Number n) return n.doubleValue();
    if (raw instanceof String s && !s.isBlank()) {
      try {
        return Double.parseDouble(s.trim());
      } catch (NumberFormatException ignored) {
        // fall through to doctor fee
      }
    }
    if (appt == null) return 0;
    Double fee = enrichment.consultationFee(appt.getDoctorId());
    return fee == null ? 0 : fee;
  }

  private JsonNode paymentNodeFromWebhook(JsonNode root) {
    JsonNode nested = root.path("payload").path("payment").path("entity");
    if (!nested.isMissingNode() && nested.isObject()) return nested;
    if (root.path("payload").path("qr_code").path("entity").isObject()
        && root.has("payload")
        && root.path("payload").has("payment")) {
      return root.path("payload").path("payment").path("entity");
    }
    return root;
  }

  private static long amountPaise(JsonNode verified, long expectedPaise) {
    JsonNode amount = verified.get("amount");
    if (amount == null || amount.isNull()) return 0;
    long n = amount.isNumber() ? amount.asLong() : 0;
    if (n <= 0) return 0;
    if (expectedPaise > 0 && n == expectedPaise) return n;
    if (expectedPaise > 0 && Math.round(n * 100.0) == expectedPaise) return expectedPaise;
    return n >= 100 ? n : Math.round(n * 100.0);
  }

  private static boolean isCaptured(String status) {
    if (status == null) return false;
    String s = status.trim().toUpperCase();
    return s.equals("CAPTURED")
        || s.equals("SUCCESS")
        || s.equals("PAID")
        || s.equals("COMPLETED")
        || s.equals("AUTHORIZED");
  }

  private static boolean isFailure(String status) {
    if (status == null) return false;
    String s = status.trim().toUpperCase();
    return s.contains("FAIL") || s.equals("CANCELLED") || s.equals("CANCELED");
  }

  private static String nextPaymentId() {
    long n = Math.floorMod(System.currentTimeMillis(), 100_000_000L);
    int r = ThreadLocalRandom.current().nextInt(10, 99);
    return "PAY" + n + r;
  }

  private static String str(Map<String, Object> body, String key) {
    if (body == null || !body.containsKey(key) || body.get(key) == null) return "";
    return String.valueOf(body.get(key)).trim();
  }

  private static String text(JsonNode node, String field) {
    if (node == null || node.isMissingNode() || node.isNull()) return "";
    JsonNode v = node.get(field);
    if (v == null || v.isNull() || v.isMissingNode()) return "";
    String s = v.asText("");
    return s == null || "null".equals(s) ? "" : s.trim();
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return "";
    for (String v : values) {
      if (v != null && !v.isBlank()) return v.trim();
    }
    return "";
  }

  private static String nullTo(String v) {
    return v == null ? "" : v;
  }

  private static String nullTo(String v, String d) {
    return v == null || v.isBlank() ? d : v;
  }

  private static boolean blank(String v) {
    return v == null || v.isBlank();
  }

  private static String urlEnc(String v) {
    return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
  }
}
