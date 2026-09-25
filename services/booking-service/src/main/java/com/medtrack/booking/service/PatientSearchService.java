package com.medtrack.booking.service;

import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.domain.PatientEntity;
import com.medtrack.booking.repo.AppointmentRepository;
import com.medtrack.booking.repo.PatientRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Lookup existing patients for voice/desk booking — does not create bookings. */
@Service
public class PatientSearchService {
  private final PatientRepository patientRepo;
  private final AppointmentRepository appointmentRepo;

  public PatientSearchService(
      PatientRepository patientRepo, AppointmentRepository appointmentRepo) {
    this.patientRepo = patientRepo;
    this.appointmentRepo = appointmentRepo;
  }

  public Map<String, Object> searchByName(String rawName) {
    String name = rawName == null ? "" : rawName.trim();
    if (name.length() < 2) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "name must be at least 2 characters");
    }

    LinkedHashMap<String, Map<String, Object>> unique = new LinkedHashMap<>();
    for (PatientEntity p : patientRepo.findTop20ByNameContainingIgnoreCaseOrderByNameAsc(name)) {
      unique.put(key(p.getName(), p.getPhone()), toMap(p));
    }
    for (AppointmentEntity a :
        appointmentRepo.findFirst20ByPatientNameContainingIgnoreCaseOrderByCreatedDateDesc(name)) {
      String key = key(a.getPatientName(), a.getPhoneNumber());
      unique.putIfAbsent(key, fromAppointment(a));
    }

    List<Map<String, Object>> patients = new ArrayList<>(unique.values());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("count", patients.size());
    out.put("name", name);
    out.put("patients", patients);
    return out;
  }

  private static String key(String name, String phone) {
    String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    String p = phone == null ? "" : phone.replaceAll("\\D", "");
    return n + "|" + p;
  }

  private static Map<String, Object> toMap(PatientEntity p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", p.getId());
    m.put("name", p.getName());
    m.put("phone", p.getPhone());
    m.put("age", p.getAge());
    m.put("gender", p.getGender());
    m.put("email", p.getEmail());
    m.put("address", p.getAddress());
    m.put("source", "patients");
    return m;
  }

  private static Map<String, Object> fromAppointment(AppointmentEntity a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getPatientId());
    m.put("name", a.getPatientName());
    m.put("phone", a.getPhoneNumber());
    m.put("age", a.getPatientAge());
    m.put("gender", a.getGender());
    m.put("email", "");
    m.put("address", a.getAddress());
    m.put("source", "appointments");
    return m;
  }
}
