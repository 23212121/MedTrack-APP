package com.medtrack.booking.service;

import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.domain.CareChatEntity;
import com.medtrack.booking.repo.AppointmentRepository;
import com.medtrack.booking.repo.CareChatRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HospitalPatientListService {
  private final AppointmentRepository appointmentRepo;
  private final CareChatRepository chatRepo;
  private final AppointmentEnrichmentService enrichment;

  public HospitalPatientListService(
      AppointmentRepository appointmentRepo,
      CareChatRepository chatRepo,
      AppointmentEnrichmentService enrichment) {
    this.appointmentRepo = appointmentRepo;
    this.chatRepo = chatRepo;
    this.enrichment = enrichment;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> list(Long hospitalId) {
    if (hospitalId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hospital id is required");
    }
    List<AppointmentEntity> appts = appointmentRepo.findByHospitalIdOrderByCreatedDateDesc(hospitalId);
    Map<String, List<CareChatEntity>> chats =
        appts.isEmpty()
            ? Map.of()
            : chatRepo.findByAppointmentIdIn(appts.stream().map(AppointmentEntity::getId).toList()).stream()
                .filter(row -> row.getAppointmentId() != null && !row.getAppointmentId().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(CareChatEntity::getAppointmentId));
    Set<String> patients = new LinkedHashSet<>();
    Set<String> doctors = new LinkedHashSet<>();
    List<Map<String, Object>> rows = new ArrayList<>();
    for (AppointmentEntity appt : appts) {
      if (appt.getPatientName() != null && !appt.getPatientName().isBlank()) {
        patients.add(appt.getPatientName().trim().toLowerCase());
      }
      if (appt.getDoctorId() != null && !appt.getDoctorId().isBlank()) {
        doctors.add(appt.getDoctorId());
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("appointmentId", appt.getId());
      row.put("hospitalId", appt.getHospitalId());
      row.put("hospitalName", enrichment.hospitalName(appt.getHospitalId()));
      row.put("patientName", appt.getPatientName());
      row.put("patientPhone", appt.getPhoneNumber());
      row.put("patientId", appt.getPatientId());
      row.put("doctorId", appt.getDoctorId());
      row.put("doctorName", enrichment.doctorName(appt.getDoctorId()));
      row.put("tokenNumber", appt.getTokenNumber());
      row.put("status", appt.getStatus());
      row.put("appointmentDate", appt.getAppointmentDate() != null ? appt.getAppointmentDate().toString() : "");
      row.put("createdDate", appt.getCreatedDate() != null ? appt.getCreatedDate().toString() : "");
      row.put("createdUser", firstNonBlank(appt.getCreatedBy(), appt.getBookedBy()));
      List<CareChatEntity> thread = chats.getOrDefault(appt.getId(), List.of());
      List<String> otherAts = CareChatService.otherMessageAts(thread, "HOSPITAL");
      row.put("chatCount", thread.size());
      row.put("unreadCount", otherAts.size());
      row.put("otherMessageAts", otherAts);
      row.put("chatLink", "/patient-list?chat=" + appt.getId());
      rows.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("hospitalId", hospitalId);
    out.put("hospitalName", enrichment.hospitalName(hospitalId));
    out.put("totalPatients", patients.size());
    out.put("totalDoctors", doctors.size());
    out.put("totalBookings", rows.size());
    out.put("rows", rows);
    return out;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return "";
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return "";
  }
}
