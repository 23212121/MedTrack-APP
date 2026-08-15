package com.medtrack.booking.service;

import com.medtrack.booking.domain.DoctorClinicEntity;
import com.medtrack.booking.domain.DoctorContactEntity;
import com.medtrack.booking.domain.DoctorPersonalEntity;
import com.medtrack.booking.domain.HospitalEntity;
import com.medtrack.booking.domain.LoginEntity;
import com.medtrack.booking.domain.PatientEntity;
import com.medtrack.booking.domain.UserDetailsEntity;
import com.medtrack.booking.repo.DoctorClinicRepository;
import com.medtrack.booking.repo.DoctorContactRepository;
import com.medtrack.booking.repo.DoctorPersonalRepository;
import com.medtrack.booking.repo.HospitalRepository;
import com.medtrack.booking.repo.LoginRepository;
import com.medtrack.booking.repo.PatientRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private final HospitalRepository hospitalRepo;
  private final LoginRepository loginRepo;
  private final DoctorPersonalRepository personalRepo;
  private final DoctorContactRepository contactRepo;
  private final DoctorClinicRepository clinicRepo;
  private final PatientRepository patientRepo;
  private final UserRegistrationService userRegistrationService;

  public AuthService(
      HospitalRepository hospitalRepo,
      LoginRepository loginRepo,
      DoctorPersonalRepository personalRepo,
      DoctorContactRepository contactRepo,
      DoctorClinicRepository clinicRepo,
      PatientRepository patientRepo,
      UserRegistrationService userRegistrationService) {
    this.hospitalRepo = hospitalRepo;
    this.loginRepo = loginRepo;
    this.personalRepo = personalRepo;
    this.contactRepo = contactRepo;
    this.clinicRepo = clinicRepo;
    this.patientRepo = patientRepo;
    this.userRegistrationService = userRegistrationService;
  }

  /**
   * Login with either hospital ID or user ID (doctor id / email).
   *
   * <pre>
   * { "loginType": "HOSPITAL"|"USER", "id": "...", "password": "..." }
   * </pre>
   */
  public Map<String, Object> login(Map<String, Object> body) {
    String loginType = text(body, "loginType");
    String id = text(body, "id");
    String password = text(body, "password");
    if (id == null || id.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
    }
    if (password == null || password.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
    }
    if (loginType == null || loginType.isBlank()) {
      loginType = looksLikeHospitalId(id) ? "HOSPITAL" : "USER";
    }

    return switch (loginType.trim().toUpperCase()) {
      case "HOSPITAL" -> loginHospital(id.trim(), password);
      case "USER" -> loginUser(id.trim(), password);
      case "PATIENT" -> loginPatient(id.trim(), password);
      default ->
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "loginType must be HOSPITAL, USER, or PATIENT");
    };
  }

  /** Register a patient portal account (phone + password). */
  public Map<String, Object> registerPatient(Map<String, Object> body) {
    String phone = normalizePhone(text(body, "phone"));
    String password = text(body, "password");
    String name = text(body, "name");
    if (phone == null || phone.length() < 8) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid phone is required");
    }
    if (password == null || password.length() < 4) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 4 characters");
    }
    if (name == null || name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
    }
    if (patientRepo.existsByPhone(phone)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone already registered");
    }

    PatientEntity p = new PatientEntity();
    p.setPhone(phone);
    p.setPassword(password);
    p.setName(name.trim());
    p.setAge(intOrNull(body.get("age")));
    p.setGender(text(body, "gender"));
    p.setEmail(text(body, "email"));
    p.setAddress(text(body, "address"));
    patientRepo.save(p);

    Map<String, Object> out = new HashMap<>();
    out.put("message", "Patient account created");
    out.put("patientId", p.getId());
    out.put("phone", p.getPhone());
    out.put("name", p.getName());
    return out;
  }

  private Map<String, Object> loginHospital(String hospitalIdRaw, String password) {
    Long hospitalId;
    try {
      hospitalId = Long.parseLong(hospitalIdRaw);
    } catch (NumberFormatException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hospital ID must be a number");
    }

    // Prefer svc.login (hospital credentials)
    Optional<LoginEntity> loginOpt =
        loginRepo.findByLoginTypeAndLoginIdIgnoreCaseAndStatus("HOSPITAL", hospitalIdRaw, "ACTIVE");
    if (loginOpt.isEmpty()) {
      loginOpt =
          loginRepo.findByLoginTypeAndHospitalId("HOSPITAL", hospitalId)
              .filter(l -> "ACTIVE".equalsIgnoreCase(l.getStatus()));
    }
    if (loginOpt.isPresent()) {
      LoginEntity login = loginOpt.get();
      if (!login.getPassword().equals(password)) {
        throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Invalid hospital ID or password");
      }
      HospitalEntity h =
          hospitalRepo
              .findById(login.getHospitalId() != null ? login.getHospitalId() : hospitalId)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.UNAUTHORIZED, "Invalid hospital ID or password"));
      return hospitalLoginResponse(h, login.getDisplayName());
    }

    // Legacy fallback: hospitals.admin_password
    HospitalEntity h =
        hospitalRepo
            .findById(hospitalId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid hospital ID or password"));
    if (h.getAdminPassword() == null || h.getAdminPassword().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "No password set for this hospital. Re-register admin credentials.");
    }
    if (!h.getAdminPassword().equals(password)) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Invalid hospital ID or password");
    }
    return hospitalLoginResponse(h, null);
  }

  private Map<String, Object> hospitalLoginResponse(HospitalEntity h, String displayName) {
    Map<String, Object> out = new HashMap<>();
    out.put("message", "Signed in with hospital ID");
    out.put("loginType", "HOSPITAL");
    out.put(
        "username",
        displayName != null && !displayName.isBlank()
            ? displayName
            : (h.getAdminEmail() != null ? h.getAdminEmail() : String.valueOf(h.getId())));
    out.put("userId", String.valueOf(h.getId()));
    out.put("hospitalId", h.getId());
    out.put("hospitalName", h.getHospitalName());
    out.put("hospitalCode", h.getHospitalCode());
    out.put("role", "HOSPITAL_ADMIN");
    return out;
  }

  private Map<String, Object> loginUser(String userId, String password) {
    // Prefer svc.login for USER (doctor) credentials
    var loginOpt =
        loginRepo.findByLoginTypeAndLoginIdIgnoreCaseAndStatus("USER", userId.trim(), "ACTIVE");
    if (loginOpt.isPresent()) {
      LoginEntity login = loginOpt.get();
      if (!login.getPassword().equals(password)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user ID or password");
      }
      String doctorId =
          login.getDoctorId() != null && !login.getDoctorId().isBlank()
              ? login.getDoctorId()
              : login.getLoginId();
      DoctorPersonalEntity personal =
          personalRepo
              .findById(doctorId)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.UNAUTHORIZED, "Invalid user ID or password"));
      Long hospitalId = login.getHospitalId();
      String hospitalName = null;
      DoctorClinicEntity clinic = clinicRepo.findById(doctorId).orElse(null);
      if (hospitalId == null && clinic != null) {
        hospitalId = clinic.getHospitalId();
      }
      if (clinic != null) {
        hospitalName = clinic.getHospitalName();
      }
      if (hospitalId != null) {
        HospitalEntity h = hospitalRepo.findById(hospitalId).orElse(null);
        if (h != null && (hospitalName == null || hospitalName.isBlank())) {
          hospitalName = h.getHospitalName();
        }
      }
      if (hospitalId == null) {
        throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "This user is not linked to a hospital. Register under a hospital ID first.");
      }
      String display =
          login.getDisplayName() != null && !login.getDisplayName().isBlank()
              ? login.getDisplayName()
              : joinName(personal.getFirstName(), personal.getLastName(), doctorId);
      Map<String, Object> out = new HashMap<>();
      out.put("message", "Signed in with user ID");
      out.put("loginType", "USER");
      out.put("username", display);
      out.put("userId", doctorId);
      out.put("doctorId", doctorId);
      out.put("hospitalId", hospitalId);
      out.put("hospitalName", hospitalName);
      out.put("role", "DOCTOR");
      return out;
    }

    DoctorPersonalEntity personal = resolveDoctor(userId);
    if (personal.getLoginPassword() == null || personal.getLoginPassword().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "No password set for this user. Set a login password during doctor registration.");
    }
    if (!personal.getLoginPassword().equals(password)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user ID or password");
    }

    Long hospitalId = null;
    String hospitalName = null;
    DoctorClinicEntity clinic = clinicRepo.findById(personal.getDoctorId()).orElse(null);
    if (clinic != null) {
      hospitalId = clinic.getHospitalId();
      hospitalName = clinic.getHospitalName();
    }
    if (hospitalId != null) {
      HospitalEntity h = hospitalRepo.findById(hospitalId).orElse(null);
      if (h != null && (hospitalName == null || hospitalName.isBlank())) {
        hospitalName = h.getHospitalName();
      }
    }
    if (hospitalId == null) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "This user is not linked to a hospital. Register under a hospital ID first.");
    }

    String display =
        joinName(personal.getFirstName(), personal.getLastName(), personal.getDoctorId());

    Map<String, Object> out = new HashMap<>();
    out.put("message", "Signed in with user ID");
    out.put("loginType", "USER");
    out.put("username", display);
    out.put("userId", personal.getDoctorId());
    out.put("doctorId", personal.getDoctorId());
    out.put("hospitalId", hospitalId);
    out.put("hospitalName", hospitalName);
    out.put("role", "DOCTOR");
    return out;
  }

  private DoctorPersonalEntity resolveDoctor(String userId) {
    // Prefer doctor_id exact match
    if (personalRepo.existsById(userId)) {
      return personalRepo.findById(userId).orElseThrow();
    }
    // Fall back to email
    DoctorContactEntity contact =
        contactRepo
            .findFirstByEmailIgnoreCase(userId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid user ID or password"));
    return personalRepo
        .findById(contact.getDoctorId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid user ID or password"));
  }

  private static boolean looksLikeHospitalId(String id) {
    try {
      long n = Long.parseLong(id.trim());
      return n >= 10001L;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private static String joinName(String first, String last, String fallback) {
    StringBuilder sb = new StringBuilder();
    if (first != null && !first.isBlank()) sb.append(first.trim());
    if (last != null && !last.isBlank()) {
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(last.trim());
    }
    return sb.isEmpty() ? fallback : sb.toString();
  }

  private Map<String, Object> loginPatient(String idRaw, String password) {
    String raw = idRaw.trim();
    if (!raw.isEmpty()) {
      try {
        UserDetailsEntity u = userRegistrationService.authenticateLogin(raw, password);
        return patientLoginResponse(u.getUserName(), u.getUserId(), u.getPhone());
      } catch (ResponseStatusException ex) {
        if (ex.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
          throw ex;
        }
        // fall through to legacy patients table (phone-only accounts)
      }
    }

    String phone = normalizePhone(idRaw);
    if (phone.length() < 4) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user ID or password");
    }
    PatientEntity p =
        patientRepo
            .findByPhone(phone)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid user ID or password"));
    if (!p.getPassword().equals(password)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user ID or password");
    }

    return patientLoginResponse(p.getName(), p.getId(), p.getPhone());
  }

  private Map<String, Object> patientLoginResponse(
      String name, String userId, String phone) {
    // Patient ID is the login User ID (USR000001) for user_details accounts.
    String patientId = userId != null && !userId.isBlank() ? userId.trim() : null;
    if (patientId == null && phone != null && !phone.isBlank()) {
      patientId =
          patientRepo
              .findByPhone(normalizePhone(phone))
              .map(PatientEntity::getId)
              .orElse(normalizePhone(phone));
    }
    Map<String, Object> out = new HashMap<>();
    out.put("message", "Signed in as patient");
    out.put("loginType", "PATIENT");
    out.put("username", name);
    out.put("userId", userId);
    out.put("patientId", patientId);
    out.put("patientUserId", userId);
    out.put("patientName", name);
    if (phone != null && !phone.isBlank()) {
      out.put("patientPhone", phone);
    }
    out.put("role", "PATIENT");
    return out;
  }

  private static String normalizePhone(String raw) {
    if (raw == null) return "";
    return raw.replaceAll("\\D", "");
  }

  private static Integer intOrNull(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(String.valueOf(v));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static String text(Map<String, Object> body, String key) {
    Object v = body.get(key);
    return v == null ? null : String.valueOf(v);
  }
}
