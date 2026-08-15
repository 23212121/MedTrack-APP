package com.medtrack.doctor.service;

import com.medtrack.doctor.domain.DoctorRegistrationEntity;
import com.medtrack.doctor.dto.DoctorRegistrationRequest;
import com.medtrack.doctor.repo.DoctorRegistrationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DoctorRegistrationService {
  private final DoctorRegistrationRepository repo;

  public DoctorRegistrationService(DoctorRegistrationRepository repo) {
    this.repo = repo;
  }

  public List<DoctorRegistrationEntity> findAll() {
    return repo.findAllByOrderByCreatedDateDesc();
  }

  public DoctorRegistrationEntity findByDoctorId(String doctorId) {
    return repo.findByDoctorId(doctorId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Doctor not found: " + doctorId));
  }

  @Transactional
  public DoctorRegistrationEntity register(DoctorRegistrationRequest req) {
    if (req.getPassword() == null || !req.getPassword().equals(req.getConfirmPassword())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password and Confirm Password must match");
    }
    if (req.getPassword().length() < 6) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
    }
    if (repo.existsByEmailIgnoreCase(req.getEmail().trim())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    }
    if (repo.existsByUsernameIgnoreCase(req.getUsername().trim())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
    }
    if (repo.existsByMedicalRegistrationNumberIgnoreCase(
        req.getMedicalRegistrationNumber().trim())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Medical registration number already exists");
    }

    DoctorRegistrationEntity e = new DoctorRegistrationEntity();
    e.setDoctorId(generateDoctorId());

    e.setFirstName(trim(req.getFirstName()));
    e.setMiddleName(blankToNull(req.getMiddleName()));
    e.setLastName(trim(req.getLastName()));
    e.setGender(trim(req.getGender()));
    e.setDateOfBirth(req.getDateOfBirth());
    e.setProfilePhoto(blankToNull(req.getProfilePhoto()));
    e.setBloodGroup(blankToNull(req.getBloodGroup()));
    e.setMaritalStatus(blankToNull(req.getMaritalStatus()));

    e.setMobileNumber(digits(req.getMobileNumber()));
    e.setAlternateMobileNumber(blankToNull(digitsOrNull(req.getAlternateMobileNumber())));
    e.setEmail(trim(req.getEmail()).toLowerCase());
    e.setEmergencyContactNumber(blankToNull(digitsOrNull(req.getEmergencyContactNumber())));
    e.setResidentialAddress(trim(req.getResidentialAddress()));
    e.setCity(trim(req.getCity()));
    e.setState(trim(req.getState()));
    e.setCountry(trim(req.getCountry()));
    e.setPostalCode(trim(req.getPostalCode()));

    e.setMedicalRegistrationNumber(trim(req.getMedicalRegistrationNumber()));
    e.setMedicalCouncilName(trim(req.getMedicalCouncilName()));
    e.setRegistrationDate(req.getRegistrationDate());
    e.setRegistrationValidUntil(req.getRegistrationValidUntil());
    e.setYearsOfExperience(req.getYearsOfExperience());
    e.setCurrentDesignation(blankToNull(req.getCurrentDesignation()));
    e.setDepartment(blankToNull(req.getDepartment()));
    e.setSpecialization(trim(req.getSpecialization()));
    e.setSubSpecialization(blankToNull(req.getSubSpecialization()));
    e.setQualification(blankToNull(req.getQualification()));
    e.setMedicalCollege(blankToNull(req.getMedicalCollege()));
    e.setGraduationYear(req.getGraduationYear());

    e.setHospitalName(blankToNull(req.getHospitalName()));
    e.setClinicName(blankToNull(req.getClinicName()));
    e.setHospitalId(blankToNull(req.getHospitalId()));
    e.setBranch(blankToNull(req.getBranch()));
    e.setConsultationType(
        blankToNull(req.getConsultationType()) == null
            ? "BOTH"
            : trim(req.getConsultationType()).toUpperCase());
    e.setConsultationFee(req.getConsultationFee());
    e.setFollowUpFee(req.getFollowUpFee());
    e.setAvailableDays(blankToNull(req.getAvailableDays()));
    e.setAvailableTimeSlots(blankToNull(req.getAvailableTimeSlots()));

    e.setAadhaarNumber(blankToNull(req.getAadhaarNumber()));
    e.setPanNumber(blankToNull(req.getPanNumber()));
    e.setPassportNumber(blankToNull(req.getPassportNumber()));
    e.setGovernmentIdUpload(blankToNull(req.getGovernmentIdUpload()));

    e.setAccountHolderName(blankToNull(req.getAccountHolderName()));
    e.setBankName(blankToNull(req.getBankName()));
    e.setAccountNumber(blankToNull(req.getAccountNumber()));
    e.setIfscCode(blankToNull(req.getIfscCode()));
    e.setUpiId(blankToNull(req.getUpiId()));

    e.setUsername(trim(req.getUsername()));
    e.setPasswordHash(sha256(req.getPassword()));
    e.setSecurityQuestion(blankToNull(req.getSecurityQuestion()));
    if (req.getSecurityAnswer() != null && !req.getSecurityAnswer().isBlank()) {
      e.setSecurityAnswerHash(sha256(req.getSecurityAnswer().trim().toLowerCase()));
    }

    e.setMedicalRegistrationCertificate(blankToNull(req.getMedicalRegistrationCertificate()));
    e.setDegreeCertificate(blankToNull(req.getDegreeCertificate()));
    e.setExperienceCertificate(blankToNull(req.getExperienceCertificate()));
    e.setIdentityProof(blankToNull(req.getIdentityProof()));
    e.setAddressProof(blankToNull(req.getAddressProof()));
    e.setPassportSizePhotograph(blankToNull(req.getPassportSizePhotograph()));
    e.setDigitalSignature(blankToNull(req.getDigitalSignature()));

    e.setEmergencyContactName(blankToNull(req.getEmergencyContactName()));
    e.setEmergencyRelationship(blankToNull(req.getEmergencyRelationship()));
    e.setEmergencyContactPhone(blankToNull(digitsOrNull(req.getEmergencyContactPhone())));

    e.setLanguagesKnown(blankToNull(req.getLanguagesKnown()));
    e.setBiography(blankToNull(req.getBiography()));
    e.setAwardsAchievements(blankToNull(req.getAwardsAchievements()));
    e.setPublications(blankToNull(req.getPublications()));
    e.setResearchExperience(blankToNull(req.getResearchExperience()));
    e.setMemberships(blankToNull(req.getMemberships()));

    String status =
        req.getStatus() == null || req.getStatus().isBlank()
            ? "ACTIVE"
            : trim(req.getStatus()).toUpperCase();
    e.setStatus(status);
    e.setCreatedBy(
        req.getCreatedBy() == null || req.getCreatedBy().isBlank()
            ? "SELF_REGISTRATION"
            : trim(req.getCreatedBy()));
    e.setCreatedDate(Instant.now());
    e.setUpdatedDate(Instant.now());
    e.setRemarks(blankToNull(req.getRemarks()));

    e.setInfoCorrectConfirmed(req.isInfoCorrectConfirmed());
    e.setTermsAccepted(req.isTermsAccepted());
    e.setPrivacyPolicyAccepted(req.isPrivacyPolicyAccepted());

    DoctorRegistrationEntity saved = repo.save(e);
    System.out.printf(
        "[doctor-service] INSERT doctorId=%s name=%s %s email=%s%n",
        saved.getDoctorId(), saved.getFirstName(), saved.getLastName(), saved.getEmail());
    return saved;
  }

  private static String generateDoctorId() {
    return "DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  private static String sha256(String raw) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(dig);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }

  private static String trim(String s) {
    return s == null ? null : s.trim();
  }

  private static String blankToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private static String digits(String s) {
    return s == null ? "" : s.replaceAll("\\D", "");
  }

  private static String digitsOrNull(String s) {
    if (s == null || s.isBlank()) return null;
    return digits(s);
  }
}
