package com.medtrack.booking.service;

import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.domain.DocumentEntity;
import com.medtrack.booking.domain.HospitalEntity;
import com.medtrack.booking.domain.LabTechnicianEntity;
import com.medtrack.booking.domain.LoginEntity;
import com.medtrack.booking.domain.LoginStatus;
import com.medtrack.booking.domain.PatientEntity;
import com.medtrack.booking.domain.PatientReportEntity;
import com.medtrack.booking.domain.UserDetailsEntity;
import com.medtrack.booking.repo.AppointmentRepository;
import com.medtrack.booking.repo.DocumentRepository;
import com.medtrack.booking.repo.HospitalRepository;
import com.medtrack.booking.repo.LabTechnicianRepository;
import com.medtrack.booking.repo.LoginRepository;
import com.medtrack.booking.repo.PatientReportRepository;
import com.medtrack.booking.repo.PatientRepository;
import com.medtrack.booking.repo.UserDetailsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LabAppService {
  private final LabTechnicianRepository labRepo;
  private final LoginRepository loginRepo;
  private final HospitalRepository hospitalRepo;
  private final PatientRepository patientRepo;
  private final AppointmentRepository appointmentRepo;
  private final DocumentRepository documentRepo;
  private final PatientReportRepository reportRepo;
  private final UserDetailsRepository userDetailsRepo;
  private final DocumentUploadService documentUploadService;

  public LabAppService(
      LabTechnicianRepository labRepo,
      LoginRepository loginRepo,
      HospitalRepository hospitalRepo,
      PatientRepository patientRepo,
      AppointmentRepository appointmentRepo,
      DocumentRepository documentRepo,
      PatientReportRepository reportRepo,
      UserDetailsRepository userDetailsRepo,
      DocumentUploadService documentUploadService) {
    this.labRepo = labRepo;
    this.loginRepo = loginRepo;
    this.hospitalRepo = hospitalRepo;
    this.patientRepo = patientRepo;
    this.appointmentRepo = appointmentRepo;
    this.documentRepo = documentRepo;
    this.reportRepo = reportRepo;
    this.userDetailsRepo = userDetailsRepo;
    this.documentUploadService = documentUploadService;
  }

  public List<Map<String, Object>> list(Long hospitalId, boolean activeOnly) {
    List<LabTechnicianEntity> rows;
    if (hospitalId != null) {
      rows =
          activeOnly
              ? labRepo.findByHospitalIdAndStatusOrderByLabNameAsc(hospitalId, "ACTIVE")
              : labRepo.findByHospitalIdOrderByLabNameAsc(hospitalId);
    } else {
      rows =
          activeOnly
              ? labRepo.findByStatusOrderByLabNameAsc("ACTIVE")
              : labRepo.findAllByOrderByLabNameAsc();
    }
    Map<Long, HospitalEntity> hospitals = new LinkedHashMap<>();
    List<Long> ids =
        rows.stream().map(LabTechnicianEntity::getHospitalId).filter(Objects::nonNull).distinct().toList();
    if (!ids.isEmpty()) {
      hospitalRepo.findAllById(ids).forEach(h -> hospitals.put(h.getId(), h));
    }
    return rows.stream().map(s -> toMap(s, hospitals.get(s.getHospitalId()))).toList();
  }

  public Map<String, Object> get(String id) {
    LabTechnicianEntity lab = require(id);
    HospitalEntity hospital =
        lab.getHospitalId() == null ? null : hospitalRepo.findById(lab.getHospitalId()).orElse(null);
    return toMap(lab, hospital);
  }

  @Transactional
  public Map<String, Object> register(Long hospitalId, Map<String, Object> body, String actor) {
    String name = text(body, "labName");
    if (name == null || name.isBlank()) {
      name = text(body, "storeName");
    }
    if (name == null || name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "labName is required");
    }
    String password = text(body, "password");
    if (password == null || password.isBlank()) {
      password = "123456";
    }
    if (password.length() < 4) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 4 characters");
    }
    long seq =
        hospitalId == null ? labRepo.count() + 1 : labRepo.countByHospitalId(hospitalId) + 1;
    String prefix = hospitalId == null ? "LAB" : "LAB-" + hospitalId;
    String labCode = prefix + "-" + seq;
    while (labRepo.findByLabCodeIgnoreCase(labCode).isPresent()) {
      seq++;
      labCode = prefix + "-" + seq;
    }
    String labId = "lab-" + (hospitalId == null ? "open" : hospitalId) + "-" + seq;

    LabTechnicianEntity lab = new LabTechnicianEntity();
    lab.setId(labId);
    lab.setHospitalId(hospitalId);
    lab.setLabCode(labCode);
    lab.setLabName(name.trim());
    lab.setPhone(text(body, "phone"));
    lab.setAddress(text(body, "address"));
    HospitalEntity hospital = hospitalId == null ? null : hospitalRepo.findById(hospitalId).orElse(null);
    lab.setCity(firstNonBlank(text(body, "city"), hospital == null ? null : hospital.getCity()));
    lab.setState(firstNonBlank(text(body, "state"), hospital == null ? null : hospital.getState()));
    lab.setStatus("ACTIVE");
    lab.setCreatedAt(Instant.now());
    lab.setCreatedBy(blank(actor));
    labRepo.save(lab);

    LoginEntity login = new LoginEntity();
    login.setId("login-lab-" + (hospitalId == null ? "open" : hospitalId) + "-" + seq);
    login.setLoginType("LAB");
    login.setLoginId(labCode);
    login.setPassword(password);
    login.setHospitalId(hospitalId);
    login.setDisplayName(name.trim());
    login.setStatus(LoginStatus.ACTIVE);
    login.setCreationDate(Instant.now());
    login.setCreationUser(blank(actor));
    loginRepo.save(login);

    Map<String, Object> out = toMap(lab, hospital);
    out.put("loginId", labCode);
    out.put("message", "Lab registered. Login with lab code " + labCode);
    return out;
  }

  @Transactional
  public Map<String, Object> setStatus(String id, Long hospitalId, String status, String actor) {
    LabTechnicianEntity lab = require(id);
    if (hospitalId != null
        && lab.getHospitalId() != null
        && !hospitalId.equals(lab.getHospitalId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lab belongs to another hospital");
    }
    String next = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    if (!next.equals("ACTIVE") && !next.equals("INACTIVE")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACTIVE or INACTIVE");
    }
    lab.setStatus(next);
    lab.setUpdatedAt(Instant.now());
    lab.setUpdatedBy(blank(actor));
    labRepo.save(lab);
    loginRepo
        .findByLoginTypeAndLoginIdIgnoreCase("LAB", lab.getLabCode())
        .ifPresent(
            login -> {
              login.setStatus(LoginStatus.parse(next));
              login.setUpdateDate(Instant.now());
              login.setUpdateUser(blank(actor));
              loginRepo.save(login);
            });
    return toMap(lab, lab.getHospitalId() == null ? null : hospitalRepo.findById(lab.getHospitalId()).orElse(null));
  }

  public Map<String, Object> currentLab(LabTechnicianEntity lab) {
    HospitalEntity hospital =
        lab.getHospitalId() == null ? null : hospitalRepo.findById(lab.getHospitalId()).orElse(null);
    Map<String, Object> out = toMap(lab, hospital);
    out.put("assistantName", lab.getLabName());
    out.put("labId", lab.getLabCode());
    return Map.of("lab", out);
  }

  /**
   * Look up one patient by mobile or Aadhaar from user_details, patients, and appointments.
   * Username comes from user_details; age and gender from the patient detail row (then latest appointment).
   */
  @Transactional(readOnly = true)
  public Map<String, Object> lookupPatient(String phone, String aadhaar) {
    String phoneQ = digits(phone);
    String aadhaarQ = digits(aadhaar);
    if (phoneQ.isBlank() && aadhaarQ.length() != 12) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Enter a phone number or a 12-digit Aadhaar number");
    }

    PatientEntity patient = null;
    if (!phoneQ.isBlank()) {
      patient = patientRepo.findByPhone(phoneQ).orElse(null);
    }
    if (patient == null && aadhaarQ.length() == 12) {
      patient = patientRepo.findByAadhaarNumber(aadhaarQ).orElse(null);
    }

    String resolvedPhone = phoneQ;
    String resolvedAadhaar = aadhaarQ.length() == 12 ? aadhaarQ : "";
    String patientId = "";
    String name = "";
    String userName = "";
    Integer age = null;
    String gender = "";
    String source = "";

    if (patient != null) {
      patientId = patient.getId() == null ? "" : patient.getId();
      name = nz(patient.getName());
      String patientPhone = digits(patient.getPhone());
      if (!patientPhone.isBlank()) {
        resolvedPhone = patientPhone;
      }
      String patientAadhaar = digits(patient.getAadhaarNumber());
      if (patientAadhaar.length() == 12) {
        resolvedAadhaar = patientAadhaar;
      }
      age = patient.getAge();
      gender = nz(patient.getGender());
      source = "REGISTERED";
    }
    if (resolvedPhone.isBlank() && resolvedAadhaar.length() == 12) {
      resolvedPhone = lookupPhone(resolvedAadhaar);
    }
    if (resolvedAadhaar.length() != 12 && !resolvedPhone.isBlank()) {
      resolvedAadhaar = lookupAadhaar(resolvedPhone);
    }

    if (!resolvedPhone.isBlank()) {
      UserDetailsEntity user = userDetailsRepo.findByPhone(resolvedPhone).orElse(null);
      if (user != null) {
        userName = nz(user.getUserName());
        if (name.isBlank()) {
          name = userName;
        }
        if (source.isBlank()) {
          source = "USER";
        }
      }
      List<AppointmentEntity> appts =
          appointmentRepo.findByPhoneNumberOrderByCreatedDateDesc(resolvedPhone);
      if (!appts.isEmpty()) {
        AppointmentEntity latest = appts.get(0);
        if (name.isBlank()) {
          name = nz(latest.getPatientName());
        }
        if (age == null) {
          age = latest.getPatientAge();
        }
        if (gender.isBlank()) {
          gender = nz(latest.getGender());
        }
        if (source.isBlank()) {
          source = "APPOINTMENT";
        }
      }
    }
    if (name.isBlank() && resolvedAadhaar.length() == 12) {
      name = lookupName("", resolvedAadhaar);
      if (!name.isBlank() && source.isBlank()) {
        source = "DOCUMENT";
      }
    }
    if (userName.isBlank()) {
      userName = name;
    }
    if (name.isBlank()) {
      name = userName;
    }

    boolean found =
        !patientId.isBlank()
            || !name.isBlank()
            || !userName.isBlank()
            || age != null
            || !gender.isBlank();

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("patientId", patientId.isBlank() ? null : patientId);
    row.put("patientName", name);
    row.put("userName", userName);
    row.put("phone", resolvedPhone);
    row.put("aadhaarNumber", resolvedAadhaar.length() == 12 ? resolvedAadhaar : "");
    row.put("age", age);
    row.put("gender", gender);
    row.put("source", source);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("found", found);
    out.put("count", found ? 1 : 0);
    out.put("patients", found ? List.of(row) : List.of());
    if (found) {
      out.put("patient", row);
    }
    return out;
  }

  public LabTechnicianEntity require(String id) {
    return labRepo
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab not found"));
  }

  public LabTechnicianEntity requireByHeader(String labIdHeader, String userHeader) {
    if (labIdHeader != null && !labIdHeader.isBlank()) {
      return require(labIdHeader.trim());
    }
    if (userHeader != null && !userHeader.isBlank()) {
      return labRepo
          .findByLabCodeIgnoreCase(userHeader.trim())
          .or(() -> labRepo.findById(userHeader.trim()))
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Lab session is required"));
    }
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Lab session is required");
  }

  /**
   * Merge patients, appointments, and documents. Filter by phone, Aadhaar, and/or name.
   * Empty filters return hospital-linked patients for the lab dropdown (or a global sample).
   */
  @Transactional(readOnly = true)
  public Map<String, Object> searchPatients(
      String phone, String aadhaar, String name, Long hospitalId) {
    String phoneQ = digits(phone);
    String aadhaarQ = digits(aadhaar);
    String nameQ = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);

    Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();

    boolean filtered = !phoneQ.isBlank() || !aadhaarQ.isBlank() || !nameQ.isBlank();
    if (!phoneQ.isBlank() || !aadhaarQ.isBlank()) {
      if (!phoneQ.isBlank()) {
        patientRepo.findByPhone(phoneQ).ifPresent(p ->
            mergePatient(
                byKey,
                p.getId(),
                p.getName(),
                digits(p.getPhone()),
                digits(p.getAadhaarNumber()),
                p.getAge(),
                p.getGender(),
                "REGISTERED"));
        userDetailsRepo
            .findByPhone(phoneQ)
            .ifPresent(
                u ->
                    mergePatient(
                        byKey,
                        u.getUserId(),
                        u.getUserName(),
                        digits(u.getPhone()),
                        "",
                        null,
                        null,
                        "USER"));
        for (AppointmentEntity a : appointmentRepo.findByPhoneNumberOrderByCreatedDateDesc(phoneQ)) {
          mergePatient(
              byKey,
              a.getPatientId(),
              a.getPatientName(),
              digits(a.getPhoneNumber()),
              "",
              a.getPatientAge(),
              a.getGender(),
              "APPOINTMENT");
        }
        for (DocumentEntity d : documentRepo.findForPatientPortalByPhone(phoneQ)) {
          mergePatient(
              byKey,
              null,
              d.getPatientName(),
              digits(d.getPhoneNumber()),
              digits(d.getAadhaarNumber()),
              null,
              null,
              "DOCUMENT");
        }
      }
      if (!aadhaarQ.isBlank()) {
        patientRepo.findByAadhaarNumber(aadhaarQ).ifPresent(p ->
            mergePatient(
                byKey,
                p.getId(),
                p.getName(),
                digits(p.getPhone()),
                digits(p.getAadhaarNumber()),
                p.getAge(),
                p.getGender(),
                "REGISTERED"));
        for (DocumentEntity d : documentRepo.findForAadhaar(aadhaarQ)) {
          mergePatient(
              byKey,
              null,
              d.getPatientName(),
              digits(d.getPhoneNumber()),
              digits(d.getAadhaarNumber()),
              null,
              null,
              "DOCUMENT");
        }
      }
    } else if (hospitalId != null) {
      int added = 0;
      for (AppointmentEntity a : appointmentRepo.findByHospitalIdOrderByCreatedDateDesc(hospitalId)) {
        mergePatient(
            byKey,
            a.getPatientId(),
            a.getPatientName(),
            digits(a.getPhoneNumber()),
            "",
            a.getPatientAge(),
            a.getGender(),
            "APPOINTMENT");
        if (++added >= 400) break;
      }
      for (DocumentEntity d : documentRepo.findByHospitalIdOrderByCreationDateDesc(hospitalId)) {
        mergePatient(
            byKey,
            null,
            d.getPatientName(),
            digits(d.getPhoneNumber()),
            digits(d.getAadhaarNumber()),
            null,
            null,
            "DOCUMENT");
        if (byKey.size() >= 250) break;
      }
    } else {
      for (PatientEntity p : patientRepo.findTop200ByOrderByCreatedAtDesc()) {
        mergePatient(
            byKey,
            p.getId(),
            p.getName(),
            digits(p.getPhone()),
            digits(p.getAadhaarNumber()),
            p.getAge(),
            p.getGender(),
            "REGISTERED");
      }
      for (AppointmentEntity a : appointmentRepo.findTop200ByOrderByCreatedDateDesc()) {
        mergePatient(
            byKey,
            a.getPatientId(),
            a.getPatientName(),
            digits(a.getPhoneNumber()),
            "",
            a.getPatientAge(),
            a.getGender(),
            "APPOINTMENT");
      }
      for (DocumentEntity d : documentRepo.findTop200ByOrderByCreationDateDesc()) {
        mergePatient(
            byKey,
            null,
            d.getPatientName(),
            digits(d.getPhoneNumber()),
            digits(d.getAadhaarNumber()),
            null,
            null,
            "DOCUMENT");
      }
    }

    if (filtered && !nameQ.isBlank() && phoneQ.isBlank() && aadhaarQ.isBlank()) {
      // Name-only search: include registered patients matching name.
      for (PatientEntity p : patientRepo.findTop200ByOrderByCreatedAtDesc()) {
        String n = p.getName() == null ? "" : p.getName().toLowerCase(Locale.ROOT);
        if (n.contains(nameQ)) {
          mergePatient(
              byKey,
              p.getId(),
              p.getName(),
              digits(p.getPhone()),
              digits(p.getAadhaarNumber()),
              p.getAge(),
              p.getGender(),
              "REGISTERED");
        }
      }
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map<String, Object> row : byKey.values()) {
      String rowPhone = String.valueOf(row.getOrDefault("phone", ""));
      String rowAadhaar = String.valueOf(row.getOrDefault("aadhaarNumber", ""));
      String rowName = String.valueOf(row.getOrDefault("patientName", "")).toLowerCase(Locale.ROOT);
      boolean match = phoneQ.isBlank() && aadhaarQ.isBlank() && nameQ.isBlank();
      if (!phoneQ.isBlank() && rowPhone.contains(phoneQ)) match = true;
      if (!aadhaarQ.isBlank() && rowAadhaar.contains(aadhaarQ)) match = true;
      if (!nameQ.isBlank() && rowName.contains(nameQ)) match = true;
      if (!match) continue;
      String p = digits(rowPhone);
      if (!p.isBlank()) {
        long docs = documentRepo.findForPatientPortalByPhone(p).size();
        long reports = reportRepo.findByPatientPhoneOrderByReportDateDescCreatedAtDesc(p).size();
        row.put("documentCount", docs);
        row.put("reportCount", reports);
      } else {
        String a = digits(rowAadhaar);
        row.put("documentCount", a.isBlank() ? 0 : documentRepo.findForAadhaar(a).size());
        row.put("reportCount", 0);
      }
      rows.add(row);
    }
    if (rows.size() > 250) {
      rows = new ArrayList<>(rows.subList(0, 250));
    }
    return Map.of("count", rows.size(), "patients", rows);
  }

  @Transactional
  public Map<String, Object> uploadReports(
      LabTechnicianEntity lab,
      String patientName,
      String phone,
      String aadhaar,
      String title,
      String notes,
      Integer age,
      String gender,
      MultipartFile[] files,
      String actor) {
    if (!"ACTIVE".equalsIgnoreCase(lab.getStatus())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lab is inactive");
    }
    String phoneDigits = digits(phone);
    String aadhaarDigits = digits(aadhaar);
    if (aadhaarDigits.length() > 0 && aadhaarDigits.length() != 12) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aadhaar number must be 12 digits");
    }
    if (phoneDigits.isBlank() && aadhaarDigits.length() == 12) {
      phoneDigits = lookupPhone(aadhaarDigits);
    }
    if (aadhaarDigits.isBlank() && !phoneDigits.isBlank()) {
      aadhaarDigits = lookupAadhaar(phoneDigits);
    }
    if (phoneDigits.isBlank() && aadhaarDigits.length() != 12) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Enter a phone number or a 12-digit Aadhaar number");
    }
    String name = patientName == null ? "" : patientName.trim();
    if (name.isBlank()) {
      name = lookupName(phoneDigits, aadhaarDigits);
    }
    if (name.isBlank()) {
      name = "Patient";
    }

    DocumentEntity saved =
        documentUploadService.upload(
            lab.getHospitalId() == null ? 0L : lab.getHospitalId(),
            name,
            aadhaarDigits,
            phoneDigits,
            files,
            actor == null || actor.isBlank() ? lab.getLabCode() : actor,
            "LAB",
            lab.getId());

    persistPatientDetails(phoneDigits, aadhaarDigits, name, age, gender);

    List<Map<String, Object>> reportRows = new ArrayList<>();
    String[] slots = {
      saved.getFileUpload1(),
      saved.getFileUpload2(),
      saved.getFileUpload3(),
      saved.getFileUpload4(),
      saved.getFileUpload5()
    };
    String reportTitle = title == null || title.isBlank() ? "Lab test report" : title.trim();
    for (int i = 0; i < slots.length; i++) {
      if (slots[i] == null || slots[i].isBlank()) continue;
      PatientReportEntity r = new PatientReportEntity();
      r.setPatientPhone(phoneDigits);
      r.setPatientName(name);
      r.setHospitalId(lab.getHospitalId());
      r.setDoctorId(lab.getId());
      r.setDoctorName(lab.getLabName());
      r.setReportType("LAB");
      String fileLabel = displayName(slots[i]);
      r.setTitle(reportTitle + (i == 0 ? "" : " (" + (i + 1) + ")") + " · " + fileLabel);
      r.setDescription(notes);
      r.setFileUrl(
          "/api/patient/documents/"
              + saved.getId()
              + "/files/"
              + (i + 1)
              + "?phone="
              + phoneDigits);
      r.setReportDate(LocalDate.now());
      reportRepo.save(r);
      Map<String, Object> one = new LinkedHashMap<>();
      one.put("slot", i + 1);
      one.put("fileName", fileLabel);
      one.put("downloadUrl", r.getFileUrl());
      reportRows.add(one);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("message", "Test reports uploaded. The patient can open them on Reports.");
    out.put("document", documentUploadService.toMap(saved));
    out.put("s3Path", saved.getFilePath());
    out.put(
        "s3Uploaded",
        saved.getDestinationPath() != null && saved.getDestinationPath().startsWith("s3://"));
    out.put("storageKey", !aadhaarDigits.isBlank() ? aadhaarDigits : "phone-" + phoneDigits);
    out.put("phone", phoneDigits);
    out.put("aadhaarNumber", aadhaarDigits);
    out.put("patientName", name);
    out.put("files", reportRows);
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> listUploads(LabTechnicianEntity lab) {
    List<Map<String, Object>> rows =
        documentUploadService.listForLab(lab.getId()).stream().map(documentUploadService::toMap).toList();
    return Map.of("count", rows.size(), "documents", rows, "labName", lab.getLabName());
  }

  private void mergePatient(
      Map<String, Map<String, Object>> byKey,
      String patientId,
      String name,
      String phone,
      String aadhaar,
      Integer age,
      String gender,
      String source) {
    String p = digits(phone);
    String a = digits(aadhaar);
    if (p.isBlank() && a.isBlank() && (name == null || name.isBlank())) {
      return;
    }
    String key = !p.isBlank() ? "p:" + p : (!a.isBlank() ? "a:" + a : "n:" + name.trim().toLowerCase(Locale.ROOT));
    Map<String, Object> row = byKey.computeIfAbsent(key, k -> new LinkedHashMap<>());
    if (patientId != null && !patientId.isBlank() && isBlank(row.get("patientId"))) {
      row.put("patientId", patientId);
    }
    if (name != null && !name.isBlank()) {
      if (isBlank(row.get("patientName"))) {
        row.put("patientName", name.trim());
      }
      if ("USER".equals(source) || isBlank(row.get("userName"))) {
        row.put("userName", name.trim());
      }
    }
    if (!p.isBlank()) row.put("phone", p);
    if (a.length() == 12) row.put("aadhaarNumber", a);
    if (age != null && isBlank(row.get("age"))) row.put("age", age);
    if (gender != null && !gender.isBlank() && isBlank(row.get("gender"))) row.put("gender", gender);
    row.putIfAbsent("source", source);
    if ("REGISTERED".equals(source)) row.put("source", source);
  }

  private String lookupPhone(String aadhaar) {
    return patientRepo
        .findByAadhaarNumber(aadhaar)
        .map(p -> digits(p.getPhone()))
        .filter(s -> !s.isBlank())
        .orElseGet(
            () ->
                documentRepo.findForAadhaar(aadhaar).stream()
                    .map(d -> digits(d.getPhoneNumber()))
                    .filter(s -> !s.isBlank() && !s.startsWith("aadhaar"))
                    .findFirst()
                    .orElse(""));
  }

  private String lookupAadhaar(String phone) {
    return patientRepo
        .findByPhone(phone)
        .map(p -> digits(p.getAadhaarNumber()))
        .filter(s -> s.length() == 12)
        .orElseGet(
            () ->
                documentRepo.findForPatientPortalByPhone(phone).stream()
                    .map(d -> digits(d.getAadhaarNumber()))
                    .filter(s -> s.length() == 12)
                    .findFirst()
                    .orElse(""));
  }

  private String lookupName(String phone, String aadhaar) {
    if (!phone.isBlank()) {
      String fromUser =
          userDetailsRepo.findByPhone(phone).map(UserDetailsEntity::getUserName).orElse("");
      if (fromUser != null && !fromUser.isBlank()) return fromUser.trim();
      String fromPatient =
          patientRepo.findByPhone(phone).map(PatientEntity::getName).orElse("");
      if (fromPatient != null && !fromPatient.isBlank()) return fromPatient.trim();
      String fromAppt =
          appointmentRepo.findByPhoneNumberOrderByCreatedDateDesc(phone).stream()
              .map(AppointmentEntity::getPatientName)
              .filter(n -> n != null && !n.isBlank())
              .findFirst()
              .orElse("");
      if (!fromAppt.isBlank()) return fromAppt.trim();
    }
    if (aadhaar.length() == 12) {
      return documentRepo.findForAadhaar(aadhaar).stream()
          .map(DocumentEntity::getPatientName)
          .filter(n -> n != null && !n.isBlank())
          .findFirst()
          .orElse("");
    }
    return "";
  }

  private void persistPatientDetails(
      String phone, String aadhaar, String name, Integer age, String gender) {
    PatientEntity patient = null;
    if (!phone.isBlank()) {
      patient = patientRepo.findByPhone(phone).orElse(null);
    }
    if (patient == null && aadhaar != null && aadhaar.length() == 12) {
      patient = patientRepo.findByAadhaarNumber(aadhaar).orElse(null);
    }
    if (patient != null) {
      if (name != null && !name.isBlank()) patient.setName(name.trim());
      if (aadhaar != null && aadhaar.length() == 12) patient.setAadhaarNumber(aadhaar);
      if (age != null && age > 0) patient.setAge(age);
      if (gender != null && !gender.isBlank()) patient.setGender(gender.trim());
      patientRepo.save(patient);
    }
    if (!phone.isBlank() && name != null && !name.isBlank()) {
      String nextName = name.trim();
      userDetailsRepo
          .findByPhone(phone)
          .ifPresent(
              user -> {
                boolean taken =
                    userDetailsRepo
                        .findByUserNameIgnoreCase(nextName)
                        .filter(other -> !other.getUserId().equals(user.getUserId()))
                        .isPresent();
                if (!taken) {
                  user.setUserName(nextName);
                  userDetailsRepo.save(user);
                }
              });
    }
  }

  public Map<String, Object> toMap(LabTechnicianEntity s, HospitalEntity hospital) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", s.getId());
    m.put("hospitalId", s.getHospitalId());
    m.put("hospitalName", hospital == null ? null : hospital.getHospitalName());
    m.put("labCode", s.getLabCode());
    m.put("labId", s.getLabCode());
    m.put("labName", s.getLabName());
    m.put("assistantName", s.getLabName());
    m.put("phone", s.getPhone());
    m.put("address", s.getAddress());
    m.put("city", s.getCity());
    m.put("state", s.getState());
    m.put("status", s.getStatus());
    m.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
    return m;
  }

  private static boolean isBlank(Object v) {
    return v == null || String.valueOf(v).isBlank();
  }

  private static String displayName(String path) {
    if (path == null || path.isBlank()) return "document";
    String name = path.replace('\\', '/');
    int slash = name.lastIndexOf('/');
    if (slash >= 0) name = name.substring(slash + 1);
    return name.replaceFirst("^\\d+_", "");
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

  private static String nz(String v) {
    return v == null ? "" : v.trim();
  }

  private static String blank(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }

  private static String digits(String raw) {
    return raw == null ? "" : raw.replaceAll("\\D", "");
  }
}
