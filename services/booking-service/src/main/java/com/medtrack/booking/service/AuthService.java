package com.medtrack.booking.service;

import com.medtrack.booking.domain.HospitalEntity;
import com.medtrack.booking.domain.LoginEntity;
import com.medtrack.booking.domain.MedicalStoreEntity;
import com.medtrack.booking.domain.PatientEntity;
import com.medtrack.booking.domain.UserDetailsEntity;
import com.medtrack.booking.repo.HospitalRepository;
import com.medtrack.booking.repo.LoginRepository;
import com.medtrack.booking.repo.MedicalStoreRepository;
import com.medtrack.booking.repo.PatientRepository;
import com.medtrack.booking.repo.UserDetailsRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private final LoginRepository loginRepo;
  private final HospitalRepository hospitalRepo;
  private final PatientRepository patientRepo;
  private final UserDetailsRepository userDetailsRepo;
  private final MedicalStoreRepository storeRepo;
  private final AuthTokenService tokenService;

  public AuthService(
      LoginRepository loginRepo,
      HospitalRepository hospitalRepo,
      PatientRepository patientRepo,
      UserDetailsRepository userDetailsRepo,
      MedicalStoreRepository storeRepo,
      AuthTokenService tokenService) {
    this.loginRepo = loginRepo;
    this.hospitalRepo = hospitalRepo;
    this.patientRepo = patientRepo;
    this.userDetailsRepo = userDetailsRepo;
    this.storeRepo = storeRepo;
    this.tokenService = tokenService;
  }

  public Map<String, Object> login(Map<String, Object> body) {
    String loginType = text(body, "loginType");
    String id = text(body, "id");
    String password = text(body, "password");
    if (loginType == null || loginType.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "loginType is required");
    }
    if (id == null || id.isBlank() || password == null || password.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id and password are required");
    }
    return switch (loginType.trim().toUpperCase()) {
      case "HOSPITAL" -> loginHospital(id.trim(), password);
      case "USER" -> loginDoctor(id.trim(), password);
      case "PATIENT" -> loginPatient(id.trim(), password);
      case "MEDICAL" -> loginMedical(id.trim(), password);
      default -> throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "loginType must be HOSPITAL, USER, PATIENT, or MEDICAL");
    };
  }

  @Transactional
  public Map<String, Object> registerPatient(Map<String, Object> body) {
    String phone = digits(text(body, "phone"));
    String password = text(body, "password");
    String name = text(body, "name");
    if (phone.isBlank() || password == null || password.isBlank() || name == null || name.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "phone, password, and name are required");
    }
    if (password.length() < 4) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Password must be at least 4 characters");
    }
    if (patientRepo.findByPhone(phone).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is already registered");
    }
    PatientEntity p = new PatientEntity();
    p.setPhone(phone);
    p.setPassword(password);
    p.setName(name.trim());
    p.setAge(toInt(body.get("age")));
    p.setGender(text(body, "gender"));
    p.setEmail(text(body, "email"));
    p.setAddress(text(body, "address"));
    PatientEntity saved = patientRepo.save(p);

    Map<String, Object> out = new HashMap<>();
    out.put("message", "Patient registered successfully");
    out.put("patientId", saved.getId());
    out.put("phone", saved.getPhone());
    out.put("name", saved.getName());
    return out;
  }

  private Map<String, Object> loginHospital(String id, String password) {
    Optional<LoginEntity> login =
        loginRepo.findByLoginTypeAndLoginIdIgnoreCaseAndStatus("HOSPITAL", id, "ACTIVE");
    if (login.isEmpty()) {
      try {
        login =
            loginRepo
                .findByLoginTypeAndHospitalId("HOSPITAL", Long.parseLong(id))
                .filter(row -> "ACTIVE".equalsIgnoreCase(row.getStatus()));
      } catch (NumberFormatException ignored) {
        // not a numeric hospital id
      }
    }
    HospitalEntity hospital = null;
    if (login.isPresent() && passwordEquals(login.get().getPassword(), password)) {
      if (login.get().getHospitalId() != null) {
        hospital = hospitalRepo.findById(login.get().getHospitalId()).orElse(null);
      }
    } else {
      hospital = findHospitalByIdOrCode(id).orElse(null);
      if (hospital == null
          || hospital.getAdminPassword() == null
          || !passwordEquals(hospital.getAdminPassword(), password)) {
        throw badCredentials();
      }
    }
    if (hospital == null) {
      throw badCredentials();
    }
    Map<String, Object> claims = new HashMap<>();
    claims.put("loginType", "HOSPITAL");
    claims.put("role", "HOSPITAL_ADMIN");
    claims.put("hospitalId", hospital.getId());
    claims.put("userId", String.valueOf(hospital.getId()));
    Map<String, Object> out = baseResult("HOSPITAL", hospital.getHospitalName(), hospital);
    out.put("role", "HOSPITAL_ADMIN");
    out.put("userId", String.valueOf(hospital.getId()));
    out.put("token", tokenService.issue(claims));
    return out;
  }

  private Map<String, Object> loginDoctor(String id, String password) {
    Optional<LoginEntity> login =
        loginRepo.findByLoginTypeAndLoginIdIgnoreCaseAndStatus("USER", id, "ACTIVE");
    if (login.isEmpty()) {
      List<LoginEntity> byDoctor = loginRepo.findByLoginTypeAndDoctorIdAndStatus("USER", id, "ACTIVE");
      if (!byDoctor.isEmpty()) {
        login = Optional.of(byDoctor.get(0));
      }
    }
    if (login.isEmpty() || !passwordEquals(login.get().getPassword(), password)) {
      throw badCredentials();
    }
    LoginEntity row = login.get();
    HospitalEntity hospital =
        row.getHospitalId() == null ? null : hospitalRepo.findById(row.getHospitalId()).orElse(null);
    String doctorId =
        row.getDoctorId() != null && !row.getDoctorId().isBlank() ? row.getDoctorId() : row.getLoginId();
    Map<String, Object> claims = new HashMap<>();
    claims.put("loginType", "USER");
    claims.put("role", "DOCTOR");
    claims.put("doctorId", doctorId);
    claims.put("userId", doctorId);
    if (row.getHospitalId() != null) {
      claims.put("hospitalId", row.getHospitalId());
    }
    Map<String, Object> out =
        baseResult(
            "USER",
            row.getDisplayName() != null && !row.getDisplayName().isBlank()
                ? row.getDisplayName()
                : doctorId,
            hospital);
    out.put("role", "DOCTOR");
    out.put("userId", doctorId);
    out.put("doctorId", doctorId);
    out.put("token", tokenService.issue(claims));
    return out;
  }

  private Map<String, Object> loginMedical(String id, String password) {
    Optional<LoginEntity> login =
        loginRepo.findByLoginTypeAndLoginIdIgnoreCaseAndStatus("MEDICAL", id, "ACTIVE");
    if (login.isEmpty() || !passwordEquals(login.get().getPassword(), password)) {
      throw badCredentials();
    }
    LoginEntity row = login.get();
    MedicalStoreEntity store =
        storeRepo
            .findByStoreCodeIgnoreCase(row.getLoginId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Medical store is not registered"));
    if (!"ACTIVE".equalsIgnoreCase(store.getStatus())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Medical store is inactive");
    }
    HospitalEntity hospital =
        row.getHospitalId() == null ? null : hospitalRepo.findById(row.getHospitalId()).orElse(null);
    Map<String, Object> claims = new HashMap<>();
    claims.put("loginType", "MEDICAL");
    claims.put("role", "MEDICAL");
    claims.put("medicalStoreId", store.getId());
    claims.put("userId", store.getId());
    if (row.getHospitalId() != null) {
      claims.put("hospitalId", row.getHospitalId());
    }
    Map<String, Object> out =
        baseResult(
            "MEDICAL",
            row.getDisplayName() != null && !row.getDisplayName().isBlank()
                ? row.getDisplayName()
                : store.getStoreName(),
            hospital);
    out.put("role", "MEDICAL");
    out.put("userId", store.getId());
    out.put("medicalStoreId", store.getId());
    out.put("storeCode", store.getStoreCode());
    out.put("storeName", store.getStoreName());
    out.put("token", tokenService.issue(claims));
    return out;
  }

  private Map<String, Object> loginPatient(String id, String password) {
    String phone = digits(id);
    Optional<UserDetailsEntity> user =
        userDetailsRepo
            .findByUserId(id)
            .or(() -> userDetailsRepo.findByUserNameIgnoreCase(id))
            .or(() -> phone.isBlank() ? Optional.empty() : userDetailsRepo.findByPhone(phone));
    if (user.isPresent() && passwordEquals(user.get().getPassword(), password)) {
      UserDetailsEntity u = user.get();
      Map<String, Object> out = new HashMap<>();
      out.put("message", "Signed in");
      out.put("loginType", "PATIENT");
      out.put("username", u.getUserName());
      out.put("userId", u.getUserId());
      out.put("patientId", u.getUserId());
      out.put("patientUserId", u.getUserId());
      out.put("patientName", u.getUserName());
      out.put("patientPhone", u.getPhone() != null ? u.getPhone() : "");
      out.put("role", "PATIENT");
      return out;
    }
    Optional<PatientEntity> patient =
        phone.isBlank() ? Optional.empty() : patientRepo.findByPhone(phone);
    if (patient.isEmpty() || !passwordEquals(patient.get().getPassword(), password)) {
      throw badCredentials();
    }
    PatientEntity p = patient.get();
    Map<String, Object> out = new HashMap<>();
    out.put("message", "Signed in");
    out.put("loginType", "PATIENT");
    out.put("username", p.getName());
    out.put("userId", p.getId());
    out.put("patientId", p.getId());
    out.put("patientUserId", p.getId());
    out.put("patientName", p.getName());
    out.put("patientPhone", p.getPhone());
    out.put("role", "PATIENT");
    return out;
  }

  private Map<String, Object> baseResult(String loginType, String username, HospitalEntity hospital) {
    Map<String, Object> out = new HashMap<>();
    out.put("message", "Signed in");
    out.put("loginType", loginType);
    out.put("username", username);
    if (hospital != null) {
      out.put("hospitalId", hospital.getId());
      out.put("hospitalName", hospital.getHospitalName());
      out.put("hospitalCode", hospital.getHospitalCode());
    }
    return out;
  }

  private Optional<HospitalEntity> findHospitalByIdOrCode(String id) {
    try {
      return hospitalRepo.findById(Long.parseLong(id));
    } catch (NumberFormatException ex) {
      return hospitalRepo.findByHospitalCode(id);
    }
  }

  private static boolean passwordEquals(String stored, String given) {
    return stored != null && stored.equals(given);
  }

  private static ResponseStatusException badCredentials() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid id or password");
  }

  private static String text(Map<String, Object> body, String key) {
    Object v = body.get(key);
    return v == null ? null : String.valueOf(v);
  }

  private static String digits(String raw) {
    if (raw == null) return "";
    return raw.replaceAll("\\D", "");
  }

  private static Integer toInt(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(String.valueOf(v).trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
