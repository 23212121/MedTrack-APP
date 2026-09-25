package com.medtrack.booking.service;

import com.medtrack.booking.domain.HospitalEntity;
import com.medtrack.booking.domain.LoginEntity;
import com.medtrack.booking.domain.MedicalStoreEntity;
import com.medtrack.booking.repo.HospitalRepository;
import com.medtrack.booking.repo.LoginRepository;
import com.medtrack.booking.repo.MedicalStoreRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MedicalStoreAppService {
  private final MedicalStoreRepository storeRepo;
  private final LoginRepository loginRepo;
  private final HospitalRepository hospitalRepo;

  public MedicalStoreAppService(
      MedicalStoreRepository storeRepo, LoginRepository loginRepo, HospitalRepository hospitalRepo) {
    this.storeRepo = storeRepo;
    this.loginRepo = loginRepo;
    this.hospitalRepo = hospitalRepo;
  }

  public List<Map<String, Object>> list(Long hospitalId, boolean activeOnly) {
    List<MedicalStoreEntity> rows;
    if (hospitalId != null) {
      rows =
          activeOnly
              ? storeRepo.findByHospitalIdAndStatusOrderByStoreNameAsc(hospitalId, "ACTIVE")
              : storeRepo.findByHospitalIdOrderByStoreNameAsc(hospitalId);
    } else {
      rows =
          activeOnly
              ? storeRepo.findByStatusOrderByStoreNameAsc("ACTIVE")
              : storeRepo.findAllByOrderByStoreNameAsc();
    }
    Map<Long, HospitalEntity> hospitals = new HashMap<>();
    List<Long> ids =
        rows.stream().map(MedicalStoreEntity::getHospitalId).filter(Objects::nonNull).distinct().toList();
    if (!ids.isEmpty()) {
      hospitalRepo.findAllById(ids).forEach(h -> hospitals.put(h.getId(), h));
    }
    return rows.stream().map(s -> toMap(s, hospitals.get(s.getHospitalId()))).toList();
  }

  public Map<String, Object> get(String id) {
    MedicalStoreEntity store = require(id);
    HospitalEntity hospital =
        store.getHospitalId() == null ? null : hospitalRepo.findById(store.getHospitalId()).orElse(null);
    return toMap(store, hospital);
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
    HospitalEntity hospital = hospitalRepo.findById(hospitalId).orElse(null);
    store.setCity(firstNonBlank(text(body, "city"), hospital == null ? null : hospital.getCity()));
    store.setState(firstNonBlank(text(body, "state"), hospital == null ? null : hospital.getState()));
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

    Map<String, Object> out = toMap(store, hospital);
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
    return toMap(store, hospitalRepo.findById(store.getHospitalId()).orElse(null));
  }

  public MedicalStoreEntity require(String id) {
    return storeRepo
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medical store not found"));
  }

  public Map<String, Object> toMap(MedicalStoreEntity s) {
    HospitalEntity hospital =
        s.getHospitalId() == null ? null : hospitalRepo.findById(s.getHospitalId()).orElse(null);
    return toMap(s, hospital);
  }

  public Map<String, Object> toMap(MedicalStoreEntity s, HospitalEntity hospital) {
    String city = firstNonBlank(s.getCity(), hospital == null ? null : hospital.getCity());
    String state = firstNonBlank(s.getState(), hospital == null ? null : hospital.getState());
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", s.getId());
    m.put("hospitalId", s.getHospitalId());
    m.put("hospitalName", hospital == null ? null : hospital.getHospitalName());
    m.put("storeCode", s.getStoreCode());
    m.put("storeName", s.getStoreName());
    m.put("phone", s.getPhone());
    m.put("address", s.getAddress());
    m.put("city", city);
    m.put("state", state);
    m.put("status", s.getStatus());
    m.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
    return m;
  }

  private static String text(Map<String, Object> body, String key) {
    Object v = body.get(key);
    return v == null ? null : String.valueOf(v);
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a.trim();
    if (b != null && !b.isBlank()) return b.trim();
    return null;
  }

  private static String blank(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }
}
