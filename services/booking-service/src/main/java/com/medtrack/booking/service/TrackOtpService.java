package com.medtrack.booking.service;

import com.medtrack.booking.domain.TrackOtpConfigEntity;
import com.medtrack.booking.domain.TrackOtpEntity;
import com.medtrack.booking.repo.TrackOtpConfigRepository;
import com.medtrack.booking.repo.TrackOtpRepository;
import com.medtrack.common.dto.NotifyRequest;
import com.medtrack.notify.service.NotificationAppService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrackOtpService {
  private static final Logger log = LoggerFactory.getLogger(TrackOtpService.class);
  private static final String CONFIG_ID = "DEFAULT";
  private static final Duration OTP_TTL = Duration.ofMinutes(5);

  private final TrackOtpConfigRepository configRepo;
  private final TrackOtpRepository otpRepo;
  private final NotificationAppService notifications;
  private final String smsProvider;

  public TrackOtpService(
      TrackOtpConfigRepository configRepo,
      TrackOtpRepository otpRepo,
      NotificationAppService notifications,
      @Value("${medtrack.sms-provider:console}") String smsProvider) {
    this.configRepo = configRepo;
    this.otpRepo = otpRepo;
    this.notifications = notifications;
    this.smsProvider = smsProvider == null ? "console" : smsProvider.trim();
  }

  public boolean otpEnabled() {
    TrackOtpConfigEntity row =
        configRepo.findById(CONFIG_ID).orElseGet(this::defaultConfig);
    return isOn(row.getFlag());
  }

  public Map<String, Object> flagStatus() {
    boolean on = otpEnabled();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("flag", on ? "Y" : "N");
    out.put("otpRequired", on);
    return out;
  }

  @Transactional
  public Map<String, Object> send(String rawPhone) {
    String phone = digits(rawPhone);
    if (phone.length() < 8) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid mobile number");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("phone", phone);
    if (!otpEnabled()) {
      out.put("otpRequired", false);
      out.put("message", "OTP is off. Appointment details can be shown without a code.");
      return out;
    }
    TrackOtpEntity latest = otpRepo.findFirstByPhoneOrderByCreatedAtDesc(phone).orElse(null);
    if (latest != null
        && !latest.isVerified()
        && latest.getCreatedAt() != null
        && Instant.now().isBefore(latest.getCreatedAt().plusSeconds(45))) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "OTP already sent. Wait a moment, then try again.");
    }
    String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    TrackOtpEntity row = new TrackOtpEntity();
    row.setId(UUID.randomUUID().toString());
    row.setPhone(phone);
    row.setOtpHash(hash(phone, code));
    row.setExpiresAt(Instant.now().plus(OTP_TTL));
    row.setVerified(false);
    row.setCreatedAt(Instant.now());
    otpRepo.save(row);
    sendSms(phone, code);
    out.put("otpRequired", true);
    out.put("message", "OTP sent to " + mask(phone));
    out.put("expiresInSeconds", OTP_TTL.toSeconds());
    if ("console".equalsIgnoreCase(smsProvider)) {
      out.put("debugOtp", code);
    }
    return out;
  }

  @Transactional
  public Map<String, Object> verify(String rawPhone, String rawOtp) {
    String phone = digits(rawPhone);
    String otp = rawOtp == null ? "" : rawOtp.replaceAll("\\D", "");
    if (phone.length() < 8 || otp.length() != 6) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the 6-digit OTP sent to this mobile");
    }
    if (!otpEnabled()) {
      Map<String, Object> skip = new LinkedHashMap<>();
      skip.put("verified", true);
      skip.put("otpRequired", false);
      skip.put("phone", phone);
      return skip;
    }
    TrackOtpEntity row =
        otpRepo
            .findFirstByPhoneOrderByCreatedAtDesc(phone)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request a new OTP first"));
    if (row.isVerified()) {
      Map<String, Object> already = new LinkedHashMap<>();
      already.put("verified", true);
      already.put("otpRequired", true);
      already.put("phone", phone);
      already.put("message", "Mobile already confirmed");
      return already;
    }
    if (row.getExpiresAt() == null || Instant.now().isAfter(row.getExpiresAt())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired. Request a new code.");
    }
    if (!row.getOtpHash().equalsIgnoreCase(hash(phone, otp))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incorrect OTP. Check the SMS and try again.");
    }
    row.setVerified(true);
    otpRepo.save(row);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("verified", true);
    out.put("otpRequired", true);
    out.put("phone", phone);
    out.put("message", "Mobile confirmed");
    return out;
  }

  private void sendSms(String phone, String code) {
    Map<String, String> extra = new LinkedHashMap<>();
    extra.put("otp", code);
    NotifyRequest req =
        new NotifyRequest(
            "10001",
            "otp-" + UUID.randomUUID(),
            phone,
            "TRACK_OTP",
            "Patient",
            phone,
            null,
            true,
            false,
            null,
            "MedTrack Clinic",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            extra);
    try {
      notifications.send(req);
    } catch (Exception ex) {
      log.warn("[track-otp] SMS notify failed phone={}: {}", phone, ex.getMessage());
    }
    log.info("[track-otp] OTP sent to {} (console={})", mask(phone), smsProvider);
  }

  private TrackOtpConfigEntity defaultConfig() {
    TrackOtpConfigEntity row = new TrackOtpConfigEntity();
    row.setId(CONFIG_ID);
    row.setFlag("Y");
    row.setUpdatedAt(Instant.now());
    return configRepo.save(row);
  }

  static boolean isOn(String flag) {
    if (flag == null || flag.isBlank()) return false;
    String f = flag.trim().toUpperCase();
    return f.equals("Y") || f.equals("YES") || f.equals("ON") || f.equals("TRUE") || f.equals("1");
  }

  private static String digits(String raw) {
    return raw == null ? "" : raw.replaceAll("\\D", "");
  }

  private static String mask(String phone) {
    if (phone.length() < 4) return "****";
    return "*".repeat(Math.max(0, phone.length() - 4)) + phone.substring(phone.length() - 4);
  }

  private static String hash(String phone, String otp) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest((phone + ":" + otp).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception ex) {
      throw new IllegalStateException("Could not hash OTP");
    }
  }
}
