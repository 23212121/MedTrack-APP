package com.medtrack.booking.service;

import com.medtrack.booking.domain.DepartmentEntity;
import com.medtrack.booking.domain.DoctorPersonalEntity;
import com.medtrack.booking.dto.DepartmentRequest;
import com.medtrack.booking.repo.DepartmentRepository;
import com.medtrack.booking.repo.DoctorPersonalRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DepartmentService {
  private final DepartmentRepository departmentRepo;
  private final DoctorPersonalRepository doctorRepo;

  public DepartmentService(
      DepartmentRepository departmentRepo, DoctorPersonalRepository doctorRepo) {
    this.departmentRepo = departmentRepo;
    this.doctorRepo = doctorRepo;
  }

  public List<Map<String, Object>> findAll() {
    return departmentRepo.findAll().stream().map(this::toMap).toList();
  }

  public Map<String, Object> findById(String id) {
    return toMap(getEntity(id));
  }

  public List<Map<String, Object>> findByDoctorId(String doctorId) {
    return departmentRepo.findByDoctor_DoctorIdOrderByNameAsc(doctorId).stream()
        .map(this::toMap)
        .toList();
  }

  public List<Map<String, Object>> findByHospitalId(Long hospitalId) {
    return departmentRepo.findByHospitalIdOrderByNameAsc(hospitalId).stream()
        .map(this::toMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> create(DepartmentRequest req) {
    DoctorPersonalEntity doctor = requireDoctor(req.getDoctorId());
    DepartmentEntity e = new DepartmentEntity();
    apply(e, req, doctor, true);
    DepartmentEntity saved = departmentRepo.save(e);
    return toMap(saved);
  }

  @Transactional
  public Map<String, Object> update(String id, DepartmentRequest req) {
    DepartmentEntity e = getEntity(id);
    DoctorPersonalEntity doctor = requireDoctor(req.getDoctorId());
    apply(e, req, doctor, false);
    return toMap(departmentRepo.save(e));
  }

  @Transactional
  public void delete(String id) {
    if (!departmentRepo.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found: " + id);
    }
    departmentRepo.deleteById(id);
  }

  private DepartmentEntity getEntity(String id) {
    return departmentRepo
        .findById(id)
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found: " + id));
  }

  private DoctorPersonalEntity requireDoctor(String doctorId) {
    return doctorRepo
        .findById(doctorId.trim())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid doctorId FK — doctor not found: " + doctorId));
  }

  private void apply(
      DepartmentEntity e, DepartmentRequest req, DoctorPersonalEntity doctor, boolean creating) {
    e.setDoctor(doctor);
    e.setName(req.getName().trim());
    e.setValue(req.getValue().trim());
    e.setDescription(blankToNull(req.getDescription()));
    e.setHospitalId(req.getHospitalId());
    e.setFlag(
        req.getFlag() == null || req.getFlag().isBlank()
            ? "Y"
            : req.getFlag().trim().toUpperCase());
    if (creating) {
      e.setUserCreation(
          req.getUserCreation() == null || req.getUserCreation().isBlank()
              ? "SYSTEM"
              : req.getUserCreation().trim());
      e.setCreationDate(Instant.now());
    } else {
      e.setUpdateUser(
          req.getUpdateUser() == null || req.getUpdateUser().isBlank()
              ? "SYSTEM"
              : req.getUpdateUser().trim());
    }
    e.setUpdateDate(Instant.now());
  }

  private Map<String, Object> toMap(DepartmentEntity e) {
    Map<String, Object> m = new HashMap<>();
    m.put("id", e.getId());
    m.put("doctorId", e.getDoctor() == null ? null : e.getDoctor().getDoctorId());
    m.put("name", e.getName());
    m.put("value", e.getValue());
    m.put("description", e.getDescription());
    m.put("userCreation", e.getUserCreation());
    m.put("updateDate", e.getUpdateDate());
    m.put("updateUser", e.getUpdateUser());
    m.put("creationDate", e.getCreationDate());
    m.put("flag", e.getFlag());
    m.put("hospitalId", e.getHospitalId());
    return m;
  }

  private static String blankToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }
}
