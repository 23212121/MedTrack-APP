package com.medtrack.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.booking.domain.HospitalEntity;
import com.medtrack.booking.domain.LoginEntity;
import com.medtrack.booking.repo.HospitalRepository;
import com.medtrack.booking.repo.LoginRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HospitalRegistrationService {
  public static final long HOSPITAL_ID_START = 10001L;

  private final HospitalRepository hospitalRepo;
  private final LoginRepository loginRepo;
  private final ObjectMapper mapper;

  public HospitalRegistrationService(
      HospitalRepository hospitalRepo, LoginRepository loginRepo, ObjectMapper mapper) {
    this.hospitalRepo = hospitalRepo;
    this.loginRepo = loginRepo;
    this.mapper = mapper;
  }

  public List<Map<String, Object>> list() {
    return hospitalRepo.findAll().stream()
        .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
        .map(this::toSummary)
        .toList();
  }

  public Map<String, Object> get(Long id) {
    return toDetail(require(id));
  }

  /** Next unique hospital id: 10001, 10002, … */
  public long nextHospitalId() {
    Long max = hospitalRepo.findMaxId();
    long next = (max == null ? 10000L : max) + 1;
    return Math.max(next, HOSPITAL_ID_START);
  }

  @Transactional
  public Map<String, Object> register(Map<String, Object> body) {
    String hospitalName = text(body, "hospitalName");
    if (hospitalName == null || hospitalName.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hospitalName is required");
    }
    String hospitalType = text(body, "hospitalType");
    if (hospitalType == null || hospitalType.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hospitalType is required");
    }
    String registrationNumber = text(body, "registrationNumber");
    if (registrationNumber == null || registrationNumber.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "registrationNumber is required");
    }

    long hospitalId = nextHospitalId();
    String code = String.valueOf(hospitalId);

    HospitalEntity e = new HospitalEntity();
    e.setId(hospitalId);
    e.setHospitalCode(code);
    e.setHospitalName(hospitalName.trim());
    e.setHospitalType(hospitalType);
    e.setRegistrationNumber(registrationNumber.trim());
    e.setEmail(text(body, "email"));
    e.setPrimaryContact(text(body, "primaryContact"));
    e.setCity(text(body, "city"));
    e.setState(text(body, "state"));
    e.setCountry(text(body, "country"));
    e.setSubscriptionPlan(text(body, "subscriptionPlan"));
    String adminEmail = text(body, "adminEmail");
    String adminPassword = text(body, "adminPassword");
    if (adminPassword == null || adminPassword.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "adminPassword is required for hospital login");
    }
    e.setAdminEmail(adminEmail);
    e.setAdminPassword(adminPassword);
    e.setStatus("PENDING_VERIFICATION");
    try {
      Map<String, Object> safe = new HashMap<>(body);
      // Keep password out of the audit JSON blob
      safe.remove("adminPassword");
      safe.put("id", hospitalId);
      safe.put("hospitalId", hospitalId);
      safe.put("hospitalCode", code);
      e.setRegistrationJson(mapper.writeValueAsString(safe));
    } catch (Exception ex) {
      e.setRegistrationJson("{}");
    }

    HospitalEntity saved = hospitalRepo.save(e);

    LoginEntity login = new LoginEntity();
    login.setId("login-hosp-" + saved.getId());
    login.setLoginType("HOSPITAL");
    login.setLoginId(String.valueOf(saved.getId()));
    login.setPassword(adminPassword);
    login.setHospitalId(saved.getId());
    login.setDisplayName(
        adminEmail != null && !adminEmail.isBlank() ? adminEmail : saved.getHospitalName());
    login.setStatus("ACTIVE");
    login.setCreationDate(Instant.now());
    login.setCreationUser("hospital-register");
    loginRepo.save(login);

    Map<String, Object> out = new HashMap<>();
    out.put(
        "message",
        "Hospital registered successfully with ID "
            + saved.getId()
            + ". Pending Super Admin verification.");
    out.put("hospital", toSummary(saved));
    return out;
  }

  private HospitalEntity require(Long id) {
    return hospitalRepo
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found"));
  }

  private Map<String, Object> toSummary(HospitalEntity e) {
    Map<String, Object> m = new HashMap<>();
    m.put("id", e.getId());
    m.put("hospitalId", e.getId());
    m.put("hospitalCode", e.getHospitalCode());
    m.put("hospitalName", e.getHospitalName());
    m.put("hospitalType", e.getHospitalType());
    m.put("registrationNumber", e.getRegistrationNumber());
    m.put("email", e.getEmail());
    m.put("primaryContact", e.getPrimaryContact());
    m.put("city", e.getCity());
    m.put("state", e.getState());
    m.put("country", e.getCountry());
    m.put("subscriptionPlan", e.getSubscriptionPlan());
    m.put("status", e.getStatus());
    m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
    m.put("departments", departmentsFromJson(e.getRegistrationJson()));
    return m;
  }

  private Map<String, Object> toDetail(HospitalEntity e) {
    Map<String, Object> m = toSummary(e);
    m.put("registrationJson", e.getRegistrationJson());
    return m;
  }

  private static String text(Map<String, Object> body, String key) {
    Object v = body.get(key);
    return v == null ? null : String.valueOf(v);
  }

  private List<String> departmentsFromJson(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      JsonNode node = mapper.readTree(json).get("departments");
      if (node == null || !node.isArray()) return List.of();
      List<String> out = new ArrayList<>();
      for (JsonNode item : node) {
        String name = item.asText("").trim();
        if (!name.isEmpty() && !out.contains(name)) {
          out.add(name);
        }
      }
      return out;
    } catch (Exception ex) {
      return List.of();
    }
  }
}
