package com.medtrack.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.booking.domain.*;
import com.medtrack.booking.repo.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Independent section saves for doctor registration wizard. Each section maps to its own table;
 * doctor_id is the shared FK/PK. Sections are optional and can be filled later.
 */
@Service
public class DoctorSectionService {
  private final DoctorPersonalRepository personalRepo;
  private final DoctorContactRepository contactRepo;
  private final DoctorProfessionalRepository professionalRepo;
  private final DoctorClinicRepository clinicRepo;
  private final DoctorIdentityRepository identityRepo;
  private final DoctorBankRepository bankRepo;
  private final DoctorDocumentsRepository documentsRepo;
  private final HospitalRepository hospitalRepo;
  private final LoginRepository loginRepo;
  private final ObjectMapper mapper = new ObjectMapper();

  public DoctorSectionService(
      DoctorPersonalRepository personalRepo,
      DoctorContactRepository contactRepo,
      DoctorProfessionalRepository professionalRepo,
      DoctorClinicRepository clinicRepo,
      DoctorIdentityRepository identityRepo,
      DoctorBankRepository bankRepo,
      DoctorDocumentsRepository documentsRepo,
      HospitalRepository hospitalRepo,
      LoginRepository loginRepo) {
    this.personalRepo = personalRepo;
    this.contactRepo = contactRepo;
    this.professionalRepo = professionalRepo;
    this.clinicRepo = clinicRepo;
    this.identityRepo = identityRepo;
    this.bankRepo = bankRepo;
    this.documentsRepo = documentsRepo;
    this.hospitalRepo = hospitalRepo;
    this.loginRepo = loginRepo;
  }

  /** Create empty personal master row and return doctorId (hidden from UI). */
  @Transactional
  public Map<String, Object> init() {
    return initWithId(null);
  }

  @Transactional
  public Map<String, Object> initWithId(String requestedDoctorId) {
    String doctorId = normalizeDoctorUserId(requestedDoctorId);
    if (doctorId == null) {
      doctorId = "DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    } else if (personalRepo.existsById(doctorId)
        || loginRepo.findByLoginTypeAndLoginIdIgnoreCase("USER", doctorId).isPresent()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Doctor user ID already exists: " + doctorId);
    }
    DoctorPersonalEntity p = new DoctorPersonalEntity();
    p.setDoctorId(doctorId);
    p.setCreatedDate(Instant.now());
    p.setUpdatedDate(Instant.now());
    personalRepo.save(p);
    return Map.of("doctorId", doctorId, "message", "Doctor registration started");
  }

  public Map<String, Object> getSection(String doctorId, String section) {
    ensureDoctor(doctorId);
    return switch (section) {
      case "personal" -> entityToMap(personalRepo.findById(doctorId).orElse(null));
      case "contact" -> entityToMap(contactRepo.findById(doctorId).orElse(null));
      case "professional" -> entityToMap(professionalRepo.findById(doctorId).orElse(null));
      case "clinic" -> entityToMap(clinicRepo.findById(doctorId).orElse(null));
      case "identity" -> entityToMap(identityRepo.findById(doctorId).orElse(null));
      case "bank" -> entityToMap(bankRepo.findById(doctorId).orElse(null));
      case "documents" -> entityToMap(documentsRepo.findById(doctorId).orElse(null));
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown section");
    };
  }

  /**
   * List doctors. When {@code hospitalId} is set, only doctors registered under that
   * hospital (doctor_clinic.hospital_id) are returned — used by Book Appointment.
   */
  public List<Map<String, Object>> listDoctors(Long hospitalId) {
    List<DoctorClinicEntity> clinics =
        hospitalId == null
            ? clinicRepo.findAll()
            : clinicRepo.findByHospitalIdOrderByHospitalNameAsc(hospitalId);

    List<Map<String, Object>> out = new ArrayList<>();
    for (DoctorClinicEntity clinic : clinics) {
      out.add(toDoctorSummary(clinic.getDoctorId(), clinic));
    }

    if (hospitalId == null) {
      // Include personal rows that do not yet have a clinic section.
      for (DoctorPersonalEntity personal : personalRepo.findAll()) {
        boolean already =
            out.stream().anyMatch(d -> personal.getDoctorId().equals(d.get("doctorId")));
        if (!already) {
          out.add(toDoctorSummary(personal.getDoctorId(), null));
        }
      }
    }

    out.sort(
        (a, b) ->
            String.valueOf(a.get("doctorName"))
                .compareToIgnoreCase(String.valueOf(b.get("doctorName"))));
    return out;
  }

  /**
   * Flat registration used by Doctor Registration UI — writes personal / contact /
   * professional / clinic (including hospitalId) so Book Appointment can filter.
   */
  @Transactional
  public Map<String, Object> registerFlat(JsonNode body) {
    if (text(body, "firstName") == null || text(body, "lastName") == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "firstName and lastName are required");
    }
    Long hospitalId = longVal(body, "hospitalId");
    if (hospitalId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "hospitalId is required so the doctor appears in Book Appointment");
    }
    if (!hospitalRepo.existsById(hospitalId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Hospital ID not found: " + hospitalId);
    }
    String password = text(body, "loginPassword");
    if (password == null) password = text(body, "password");
    if (password == null || password.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "loginPassword is required for User ID sign-in");
    }
    if (password.length() < 6) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
    }

    String requestedId = text(body, "doctorUserId");
    if (requestedId == null) requestedId = text(body, "doctorId");
    if (requestedId == null || requestedId.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "doctorUserId is required for User ID sign-in");
    }
    String department = text(body, "department");
    if (department == null || department.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "department is required");
    }

    Map<String, Object> started = initWithId(requestedId);
    String doctorId = String.valueOf(started.get("doctorId"));
    savePersonal(doctorId, body);
    saveContact(doctorId, body);
    saveProfessional(doctorId, body);
    saveClinic(doctorId, body);
    if (body.has("aadhaarNumber") || body.has("panNumber")) {
      saveIdentity(doctorId, body);
    }
    if (body.has("accountNumber") || body.has("bankName")) {
      saveBank(doctorId, body);
    }
    if (body.has("medicalRegistrationCertificate") || body.has("degreeCertificate")) {
      saveDocuments(doctorId, body);
    }

    upsertDoctorLogin(doctorId, hospitalId, password, text(body, "firstName"), text(body, "lastName"));

    DoctorClinicEntity clinic = clinicRepo.findById(doctorId).orElse(null);
    Map<String, Object> doctor = toDoctorSummary(doctorId, clinic);
    doctor.put("id", doctorId);
    doctor.put("status", "ACTIVE");
    return Map.of("message", "Doctor registered successfully", "doctor", doctor);
  }

  private void upsertDoctorLogin(
      String doctorId, Long hospitalId, String password, String firstName, String lastName) {
    LoginEntity login =
        loginRepo
            .findByLoginTypeAndLoginIdIgnoreCase("USER", doctorId)
            .orElseGet(LoginEntity::new);
    if (login.getId() == null) {
      login.setId("login-user-" + doctorId);
      login.setCreationDate(Instant.now());
      login.setCreationUser("doctor-register");
    } else {
      login.setUpdateDate(Instant.now());
      login.setUpdateUser("doctor-register");
    }
    login.setLoginType("USER");
    login.setLoginId(doctorId);
    login.setPassword(password);
    login.setHospitalId(hospitalId);
    login.setDoctorId(doctorId);
    String display =
        ((firstName == null ? "" : firstName.trim())
                + " "
                + (lastName == null ? "" : lastName.trim()))
            .trim();
    login.setDisplayName(display.isBlank() ? doctorId : display);
    login.setStatus(LoginStatus.ACTIVE);
    loginRepo.save(login);
  }

  private static String normalizeDoctorUserId(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String id = raw.trim().toUpperCase();
    if (!id.matches("^[A-Z0-9][A-Z0-9_-]{2,39}$")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Doctor user ID must be 3–40 characters (letters, numbers, _ or -)");
    }
    return id;
  }

  private Map<String, Object> toDoctorSummary(String doctorId, DoctorClinicEntity clinic) {
    DoctorPersonalEntity personal = personalRepo.findById(doctorId).orElse(null);
    DoctorProfessionalEntity professional = professionalRepo.findById(doctorId).orElse(null);
    DoctorContactEntity contact = contactRepo.findById(doctorId).orElse(null);

    String first = personal == null ? null : personal.getFirstName();
    String middle = personal == null ? null : personal.getMiddleName();
    String last = personal == null ? null : personal.getLastName();

    Map<String, Object> row = new HashMap<>();
    row.put("doctorId", doctorId);
    row.put("doctorName", formatDoctorName(first, middle, last, doctorId));
    row.put("firstName", first);
    row.put("middleName", middle);
    row.put("lastName", last);
    row.put("specialization", professional == null ? null : professional.getSpecialization());
    row.put("department", professional == null ? null : professional.getDepartment());
    row.put("email", contact == null ? null : contact.getEmail());
    row.put("mobileNumber", contact == null ? null : contact.getMobileNumber());
    row.put("hospitalId", clinic == null ? null : clinic.getHospitalId());
    row.put("hospitalName", clinic == null ? null : clinic.getHospitalName());
    row.put("clinicName", clinic == null ? null : clinic.getClinicName());
    row.put("consultationFee", clinic == null ? null : clinic.getConsultationFee());
    row.put("status", "ACTIVE");
    return row;
  }

  private static String formatDoctorName(
      String first, String middle, String last, String fallbackId) {
    StringBuilder sb = new StringBuilder();
    if (first != null && !first.isBlank()) sb.append(first.trim());
    if (middle != null && !middle.isBlank()) {
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(middle.trim());
    }
    if (last != null && !last.isBlank()) {
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(last.trim());
    }
    if (sb.isEmpty()) return fallbackId;
    String name = sb.toString();
    return name.regionMatches(true, 0, "Dr", 0, 2) ? name : "Dr. " + name;
  }

  @Transactional
  public Map<String, Object> saveSection(String doctorId, String section, JsonNode body) {
    ensureDoctor(doctorId);
    return switch (section) {
      case "personal" -> savePersonal(doctorId, body);
      case "contact" -> saveContact(doctorId, body);
      case "professional" -> saveProfessional(doctorId, body);
      case "clinic" -> saveClinic(doctorId, body);
      case "identity" -> saveIdentity(doctorId, body);
      case "bank" -> saveBank(doctorId, body);
      case "documents" -> saveDocuments(doctorId, body);
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown section");
    };
  }

  private Map<String, Object> savePersonal(String doctorId, JsonNode body) {
    DoctorPersonalEntity e =
        personalRepo.findById(doctorId).orElseGet(DoctorPersonalEntity::new);
    e.setDoctorId(doctorId);
    Long hid = longVal(body, "hospitalId");
    if (hid == null) {
      DoctorClinicEntity clinic = clinicRepo.findById(doctorId).orElse(null);
      if (clinic != null) hid = clinic.getHospitalId();
    }
    e.setHospitalId(hid != null ? hid : 10001L);
    e.setFirstName(text(body, "firstName"));
    e.setMiddleName(text(body, "middleName"));
    e.setLastName(text(body, "lastName"));
    e.setGender(text(body, "gender"));
    e.setDateOfBirth(date(body, "dateOfBirth"));
    e.setProfilePhoto(text(body, "profilePhoto"));
    e.setBloodGroup(text(body, "bloodGroup"));
    e.setMaritalStatus(text(body, "maritalStatus"));
    String loginPassword = text(body, "loginPassword");
    if (loginPassword == null) loginPassword = text(body, "password");
    if (loginPassword != null) e.setLoginPassword(loginPassword);
    e.setUpdatedDate(Instant.now());
    if (e.getCreatedDate() == null) e.setCreatedDate(Instant.now());
    return Map.of("message", "Personal information saved", "data", entityToMap(personalRepo.save(e)));
  }

  private Map<String, Object> saveContact(String doctorId, JsonNode body) {
    DoctorContactEntity e = contactRepo.findById(doctorId).orElseGet(DoctorContactEntity::new);
    e.setDoctorId(doctorId);
    e.setMobileNumber(text(body, "mobileNumber"));
    e.setAlternateMobileNumber(text(body, "alternateMobileNumber"));
    e.setEmail(text(body, "email"));
    e.setEmergencyContactNumber(text(body, "emergencyContactNumber"));
    e.setResidentialAddress(text(body, "residentialAddress"));
    e.setCity(text(body, "city"));
    e.setState(text(body, "state"));
    e.setCountry(text(body, "country"));
    e.setPostalCode(text(body, "postalCode"));
    return Map.of("message", "Contact information saved", "data", entityToMap(contactRepo.save(e)));
  }

  private Map<String, Object> saveProfessional(String doctorId, JsonNode body) {
    DoctorProfessionalEntity e =
        professionalRepo.findById(doctorId).orElseGet(DoctorProfessionalEntity::new);
    e.setDoctorId(doctorId);
    e.setMedicalRegistrationNumber(text(body, "medicalRegistrationNumber"));
    e.setMedicalCouncilName(text(body, "medicalCouncilName"));
    e.setRegistrationDate(date(body, "registrationDate"));
    e.setRegistrationValidUntil(date(body, "registrationValidUntil"));
    e.setYearsOfExperience(integer(body, "yearsOfExperience"));
    e.setCurrentDesignation(text(body, "currentDesignation"));
    e.setDepartment(text(body, "department"));
    e.setSpecialization(text(body, "specialization"));
    e.setSubSpecialization(text(body, "subSpecialization"));
    e.setQualification(text(body, "qualification"));
    e.setMedicalCollege(text(body, "medicalCollege"));
    e.setGraduationYear(integer(body, "graduationYear"));
    return Map.of(
        "message", "Professional information saved", "data", entityToMap(professionalRepo.save(e)));
  }

  private Map<String, Object> saveClinic(String doctorId, JsonNode body) {
    DoctorClinicEntity e = clinicRepo.findById(doctorId).orElseGet(DoctorClinicEntity::new);
    e.setDoctorId(doctorId);
    e.setHospitalName(text(body, "hospitalName"));
    e.setClinicName(text(body, "clinicName"));
    e.setHospitalId(longVal(body, "hospitalId"));
    e.setBranch(text(body, "branch"));
    e.setConsultationType(text(body, "consultationType"));
    e.setConsultationFee(dbl(body, "consultationFee"));
    e.setFollowUpFee(dbl(body, "followUpFee"));
    e.setAvailableDays(text(body, "availableDays"));
    e.setAvailableTimeSlots(text(body, "availableTimeSlots"));
    return Map.of("message", "Clinic information saved", "data", entityToMap(clinicRepo.save(e)));
  }

  private Map<String, Object> saveIdentity(String doctorId, JsonNode body) {
    DoctorIdentityEntity e = identityRepo.findById(doctorId).orElseGet(DoctorIdentityEntity::new);
    e.setDoctorId(doctorId);
    e.setAadhaarNumber(text(body, "aadhaarNumber"));
    e.setPanNumber(text(body, "panNumber"));
    e.setPassportNumber(text(body, "passportNumber"));
    e.setGovernmentIdUpload(text(body, "governmentIdUpload"));
    return Map.of("message", "Identity documents saved", "data", entityToMap(identityRepo.save(e)));
  }

  private Map<String, Object> saveBank(String doctorId, JsonNode body) {
    DoctorBankEntity e = bankRepo.findById(doctorId).orElseGet(DoctorBankEntity::new);
    e.setDoctorId(doctorId);
    e.setAccountHolderName(text(body, "accountHolderName"));
    e.setBankName(text(body, "bankName"));
    e.setAccountNumber(text(body, "accountNumber"));
    e.setIfscCode(text(body, "ifscCode"));
    e.setUpiId(text(body, "upiId"));
    return Map.of("message", "Bank details saved", "data", entityToMap(bankRepo.save(e)));
  }

  private Map<String, Object> saveDocuments(String doctorId, JsonNode body) {
    DoctorDocumentsEntity e =
        documentsRepo.findById(doctorId).orElseGet(DoctorDocumentsEntity::new);
    e.setDoctorId(doctorId);
    e.setMedicalRegistrationCertificate(text(body, "medicalRegistrationCertificate"));
    e.setDegreeCertificate(text(body, "degreeCertificate"));
    e.setExperienceCertificate(text(body, "experienceCertificate"));
    e.setIdentityProof(text(body, "identityProof"));
    e.setAddressProof(text(body, "addressProof"));
    e.setPassportSizePhotograph(text(body, "passportSizePhotograph"));
    e.setDigitalSignature(text(body, "digitalSignature"));
    return Map.of("message", "Documents saved", "data", entityToMap(documentsRepo.save(e)));
  }

  private void ensureDoctor(String doctorId) {
    if (doctorId == null || doctorId.isBlank() || !personalRepo.existsById(doctorId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found: " + doctorId);
    }
  }

  private Map<String, Object> entityToMap(Object entity) {
    if (entity == null) return Map.of();
    @SuppressWarnings("unchecked")
    Map<String, Object> map = mapper.convertValue(entity, Map.class);
    return map == null ? Map.of() : new HashMap<>(map);
  }

  private static String text(JsonNode body, String field) {
    JsonNode n = body.get(field);
    if (n == null || n.isNull()) return null;
    String v = n.asText("").trim();
    return v.isEmpty() ? null : v;
  }

  private static LocalDate date(JsonNode body, String field) {
    String v = text(body, field);
    return v == null ? null : LocalDate.parse(v);
  }

  private static Integer integer(JsonNode body, String field) {
    JsonNode n = body.get(field);
    if (n == null || n.isNull() || n.asText("").isBlank()) return null;
    return n.isNumber() ? n.intValue() : Integer.parseInt(n.asText().trim());
  }

  private static Long longVal(JsonNode body, String field) {
    JsonNode n = body.get(field);
    if (n == null || n.isNull() || n.asText("").isBlank()) return null;
    return n.isNumber() ? n.longValue() : Long.parseLong(n.asText().trim());
  }

  private static Double dbl(JsonNode body, String field) {
    JsonNode n = body.get(field);
    if (n == null || n.isNull() || n.asText("").isBlank()) return null;
    return n.isNumber() ? n.doubleValue() : Double.parseDouble(n.asText().trim());
  }
}
