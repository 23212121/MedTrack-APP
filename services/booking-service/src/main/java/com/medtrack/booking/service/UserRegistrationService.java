package com.medtrack.booking.service;

import com.medtrack.booking.domain.UserDetailsEntity;
import com.medtrack.booking.repo.UserDetailsRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserRegistrationService {
  private static final Logger log = LoggerFactory.getLogger(UserRegistrationService.class);
  private static final int MIN_PASSWORD_LEN = 4;

  private final UserDetailsRepository userDetailsRepo;
  private final JdbcTemplate jdbc;

  public UserRegistrationService(UserDetailsRepository userDetailsRepo, JdbcTemplate jdbc) {
    this.userDetailsRepo = userDetailsRepo;
    this.jdbc = jdbc;
  }

  /** Preview next User ID — USR000001, USR000002, … */
  public Map<String, Object> nextUserId() {
    long seq = nextSequenceValue();
    String userId = formatUserId(seq);
    return Map.of("userId", userId, "sequence", seq);
  }

  /**
   * Save Patient/User Registration into USER_DETAILS.
   *
   * <pre>
   * { "userName": "azherkhan", "phone": "984394375", "password": "....", "createdBy": "SELF" }
   * </pre>
   */
  @Transactional
  public Map<String, Object> register(Map<String, Object> body) {
    String userName = text(body, "userName");
    String phone = normalizePhone(text(body, "phone"));
    String password = text(body, "password");
    String email = normalizeEmail(text(body, "email"));
    String createdBy = text(body, "createdBy");

    if (userName == null || userName.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User Name cannot be blank");
    }
    if (phone == null || phone.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number cannot be blank");
    }
    if (password == null || password.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password cannot be blank");
    }
    if (password.length() < MIN_PASSWORD_LEN) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Password must contain a minimum of " + MIN_PASSWORD_LEN + " characters");
    }
    if (userDetailsRepo.existsByUserNameIgnoreCase(userName.trim())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "User Name is already registered");
    }
    if (userDetailsRepo.existsByPhone(phone)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is already registered");
    }

    // user_id is backend-generated (USR000001…) — never accepted from the client.
    long seq = nextSequenceValue();
    String userId = formatUserId(seq);

    UserDetailsEntity row = new UserDetailsEntity();
    row.setUserId(userId);
    row.setUserName(userName.trim());
    row.setPhone(phone);
    row.setEmail(email);
    row.setPassword(password);
    row.setCreatedBy(createdBy == null || createdBy.isBlank() ? "SELF" : createdBy.trim());
    userDetailsRepo.save(row);

    Map<String, Object> out = new HashMap<>();
    out.put("message", "User registered successfully. Your User ID is " + userId + ".");
    out.put("userId", userId);
    out.put("patientId", userId);
    out.put("userName", row.getUserName());
    out.put("phone", row.getPhone() != null ? row.getPhone() : "");
    out.put("email", row.getEmail() != null ? row.getEmail() : "");
    return out;
  }

  /** Persist / refresh email on user_details by phone (booking + profile). */
  @Transactional
  public void upsertEmailByPhone(String phone, String email) {
    String normalizedPhone = normalizePhone(phone);
    String normalizedEmail = normalizeEmail(email);
    if (normalizedPhone.isBlank() || normalizedEmail == null || normalizedEmail.isBlank()) {
      return;
    }
    userDetailsRepo
        .findByPhone(normalizedPhone)
        .ifPresent(
            u -> {
              u.setEmail(normalizedEmail);
              userDetailsRepo.save(u);
            });
  }

  static String normalizeEmail(String raw) {
    if (raw == null) return null;
    String e = raw.trim();
    return e.isBlank() ? null : e.toLowerCase();
  }

  /**
   * Login with USR000001, username (azherkhan), or phone (984394375).
   */
  public UserDetailsEntity authenticateLogin(String loginId, String password) {
    if (loginId == null || loginId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID is required");
    }
    if (password == null || password.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
    }

    String raw = loginId.trim();
    Optional<UserDetailsEntity> found = resolveUser(raw);
    UserDetailsEntity u =
        found.orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid user ID or password"));
    if (!u.getPassword().equals(password)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user ID or password");
    }
    return u;
  }

  public UserDetailsEntity requireByUserId(String userId) {
    return userDetailsRepo
        .findByUserId(userId.trim().toUpperCase())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));
  }

  /** @deprecated use {@link #authenticateLogin} */
  public UserDetailsEntity authenticate(String userId, String password) {
    return authenticateLogin(userId, password);
  }

  private Optional<UserDetailsEntity> resolveUser(String raw) {
    String upper = raw.toUpperCase();
    if (upper.startsWith("USR")) {
      return userDetailsRepo.findByUserId(upper);
    }
    String phone = normalizePhone(raw);
    if (phone.length() >= 4) {
      Optional<UserDetailsEntity> byPhone = userDetailsRepo.findByPhone(phone);
      if (byPhone.isPresent()) {
        return byPhone;
      }
    }
    return userDetailsRepo.findByUserNameIgnoreCase(raw.trim());
  }

  private long nextSequenceValue() {
    try {
      jdbc.execute("CREATE SEQUENCE IF NOT EXISTS svc.user_id_seq START WITH 1 INCREMENT BY 1");
      Long seq = jdbc.queryForObject("SELECT nextval('svc.user_id_seq')", Long.class);
      if (seq != null && seq > 0) {
        return seq;
      }
    } catch (Exception ex) {
      log.warn("user_id_seq unavailable, falling back to max user id scan: {}", ex.getMessage());
    }
    return maxExistingUserSequence() + 1;
  }

  private long maxExistingUserSequence() {
    try {
      Long max =
          jdbc.queryForObject(
              """
              SELECT COALESCE(MAX(CAST(SUBSTRING(user_id FROM 4) AS BIGINT)), 0)
              FROM svc.user_details
              WHERE user_id LIKE 'USR%'
              """,
              Long.class);
      return max != null ? max : 0L;
    } catch (Exception ex) {
      log.warn("Could not read max user id from user_details: {}", ex.getMessage());
      return 0L;
    }
  }

  static String formatUserId(long seq) {
    return "USR" + String.format("%06d", seq);
  }

  static String normalizePhone(String raw) {
    if (raw == null) return "";
    return raw.replaceAll("\\D", "");
  }

  private static String text(Map<String, Object> body, String key) {
    Object v = body.get(key);
    return v == null ? null : String.valueOf(v);
  }
}
