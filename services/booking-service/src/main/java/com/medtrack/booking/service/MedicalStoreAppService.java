package com.medtrack.booking.service;

import com.medtrack.booking.domain.LoginEntity;
import com.medtrack.booking.domain.MedicalStoreEntity;
import com.medtrack.booking.repo.LoginRepository;
import com.medtrack.booking.repo.MedicalStoreRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MedicalStoreAppService {
  private final MedicalStoreRepository storeRepo;
  private final LoginRepository loginRepo;

  public MedicalStoreAppService(MedicalStoreRepository storeRepo, LoginRepository loginRepo) {
    this.storeRepo = storeRepo;
    this.loginRepo = loginRepo;
  }

  public List<Map<String, Object>> list(Long hospitalId, boolean activeOnly) {
    if (hospitalId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hospitalId is required");
    }
    List<MedicalStoreEntity> rows =
        activeOnly
            ? storeRepo.findByHospitalIdAndStatusOrderByStoreNameAsc(hospitalId, "ACTIVE")
            : storeRepo.findByHospitalIdOrderByStoreNameAsc(hospitalId);
    return rows.stream().map(this::toMap).toList();
  }

  public Map<String, Object> get(String id) {
    return toMap(require(id));
  }

  @Transactional
  public Map<String, Object> register(Long hospitalId, Map<String, Object> body, String actor) {
    if (hospitalId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hospitalId is required");
    }
    String name = text(body, "storeName");
    if (name == null || name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeName is required");
    }
    String password = text(body, "password");
    if (password == null || password.isBlank()) {
      password = "123456";
    }
    if (password.length() < 4) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 4 characters");
    }
    long seq = storeRepo.countByHospitalId(hospitalId) + 1;
    String storeCode = "MED-" + hospitalId + "-" + seq;
    while (storeRepo.findByStoreCodeIgnoreCase(storeCode).isPresent()) {
      seq++;
      storeCode = "MED-" + hospitalId + "-" + seq;
    }
    String storeId = "store-" + hospitalId + "-" + seq;

    MedicalStoreEntity store = new MedicalStoreEntity();
    store.setId(storeId);
    store.setHospitalId(hospitalId);
    store.setStoreCode(storeCode);
    store.setStoreName(name.trim());
    store.setPhone(text(body, "phone"));
    store.setAddress(text(body, "address"));
    store.setStatus("ACTIVE");
    store.setCreatedAt(Instant.now());
    store.setCreatedBy(blank(actor));
    storeRepo.save(store);

    LoginEntity login = new LoginEntity();
    login.setId("login-med-" + hospitalId + "-" + seq);
    login.setLoginType("MEDICAL");
    login.setLoginId(storeCode);
    login.setPassword(password);
    login.setHospitalId(hospitalId);
    login.setDisplayName(name.trim());
    login.setStatus("ACTIVE");
    login.setCreationDate(Instant.now());
    login.setCreationUser(blank(actor));
    loginRepo.save(login);

    Map<String, Object> out = toMap(store);
    out.put("loginId", storeCode);
    out.put("message", "Medical store registered. Login with store code " + storeCode);
    return out;
  }

  @Transactional
  public Map<String, Object> setStatus(String id, Long hospitalId, String status, String actor) {
    MedicalStoreEntity store = require(id);
    if (hospitalId != null && !hospitalId.equals(store.getHospitalId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Store belongs to another hospital");
    }
    String next = status == null ? "" : status.trim().toUpperCase();
    if (!next.equals("ACTIVE") && !next.equals("INACTIVE")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACTIVE or INACTIVE");
    }
    store.setStatus(next);
    store.setUpdatedAt(Instant.now());
    store.setUpdatedBy(blank(actor));
    storeRepo.save(store);
    loginRepo
        .findByLoginTypeAndLoginIdIgnoreCase("MEDICAL", store.getStoreCode())
        .ifPresent(
            login -> {
              login.setStatus(next);
              login.setUpdateDate(Instant.now());
              login.setUpdateUser(blank(actor));
              loginRepo.save(login);
            });
    return toMap(store);
  }

  public MedicalStoreEntity require(String id) {
    return storeRepo
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medical store not found"));
  }

  public Map<String, Object> toMap(MedicalStoreEntity s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", s.getId());
    m.put("hospitalId", s.getHospitalId());
    m.put("storeCode", s.getStoreCode());
    m.put("storeName", s.getStoreName());
    m.put("phone", s.getPhone());
    m.put("address", s.getAddress());
    m.put("status", s.getStatus());
    m.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
    return m;
  }

  private static String text(Map<String, Object> body, String key) {
    Object v = body.get(key);
    return v == null ? null : String.valueOf(v);
  }

  private static String blank(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }
}
