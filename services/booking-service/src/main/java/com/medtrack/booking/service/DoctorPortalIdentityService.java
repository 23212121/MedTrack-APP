package com.medtrack.booking.service;

import com.medtrack.booking.domain.DoctorClinicEntity;
import com.medtrack.booking.domain.LoginEntity;
import com.medtrack.booking.repo.DoctorClinicRepository;
import com.medtrack.booking.repo.DoctorPersonalRepository;
import com.medtrack.booking.repo.LoginRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the logged-in doctor from the session token (preferred) and verifies:
 * Logged-in Doctor → Assigned Hospital → booking/doctor assignment.
 */
@Service
public class DoctorPortalIdentityService {
  private final AuthTokenService tokenService;
  private final LoginRepository loginRepo;
  private final DoctorClinicRepository clinicRepo;
  private final DoctorPersonalRepository personalRepo;

  public DoctorPortalIdentityService(
      AuthTokenService tokenService,
      LoginRepository loginRepo,
      DoctorClinicRepository clinicRepo,
      DoctorPersonalRepository personalRepo) {
    this.tokenService = tokenService;
    this.loginRepo = loginRepo;
    this.clinicRepo = clinicRepo;
    this.personalRepo = personalRepo;
  }

  public DoctorSession requireDoctor(HttpServletRequest request) {
    String auth = request.getHeader("Authorization");
    if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
      Map<String, Object> claims = tokenService.verify(auth.substring(7).trim());
      String loginType = stringify(claims.get("loginType"));
      String role = stringify(claims.get("role"));
      if (!"USER".equalsIgnoreCase(loginType) && !"DOCTOR".equalsIgnoreCase(role)) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Doctor login required to access the doctor portal");
      }
      String doctorId = stringify(claims.get("doctorId"));
      Long hospitalId = toLong(claims.get("hospitalId"));
      return verifyAssignment(doctorId, hospitalId);
    }

    String doctorHeader = request.getHeader("X-Doctor-Id");
    String hospitalHeader = request.getHeader("X-Hospital-Id");
    if (doctorHeader == null
        || doctorHeader.isBlank()
        || hospitalHeader == null
        || hospitalHeader.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Doctor session required. Sign in as a doctor.");
    }
    Long hospitalId = toLong(hospitalHeader);
    return verifyAssignment(doctorHeader.trim(), hospitalId);
  }

  /**
   * Confirm the doctor belongs to the hospital via svc.login and doctor_clinic.
   * Client-supplied doctor/hospital IDs are never trusted without this check.
   */
  public DoctorSession verifyAssignment(String doctorIdRaw, Long hospitalId) {
    if (doctorIdRaw == null || doctorIdRaw.isBlank() || hospitalId == null) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Doctor session required. Sign in as a doctor.");
    }
    String doctorId = doctorIdRaw.trim();

    Optional<LoginEntity> login =
        loginRepo.findByLoginTypeAndLoginIdIgnoreCaseAndStatus("USER", doctorId, "ACTIVE");
    if (login.isEmpty()) {
      List<LoginEntity> byDoctor =
          loginRepo.findByLoginTypeAndDoctorIdAndStatus("USER", doctorId, "ACTIVE");
      if (!byDoctor.isEmpty()) {
        login = Optional.of(byDoctor.get(0));
      }
    }
    if (login.isPresent()) {
      LoginEntity row = login.get();
      if (row.getHospitalId() != null && !row.getHospitalId().equals(hospitalId)) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Doctor is not assigned to this hospital");
      }
      if (row.getDoctorId() != null && !row.getDoctorId().isBlank()) {
        doctorId = row.getDoctorId().trim();
      }
    }

    DoctorClinicEntity clinic = clinicRepo.findById(doctorId).orElse(null);
    if (clinic != null
        && clinic.getHospitalId() != null
        && !clinic.getHospitalId().equals(hospitalId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Doctor is not assigned to this hospital");
    }
    if (!personalRepo.existsById(doctorId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Doctor profile not found for this login");
    }
    return new DoctorSession(doctorId, hospitalId, "DOCTOR");
  }

  private static Long toLong(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.longValue();
    try {
      return Long.parseLong(String.valueOf(v).trim());
    } catch (NumberFormatException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid hospital id");
    }
  }

  private static String stringify(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }
}
