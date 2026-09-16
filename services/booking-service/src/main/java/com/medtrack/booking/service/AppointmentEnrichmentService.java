package com.medtrack.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.domain.BookingEntity;
import com.medtrack.booking.domain.DoctorClinicEntity;
import com.medtrack.booking.domain.DoctorPersonalEntity;
import com.medtrack.booking.domain.HospitalEntity;
import com.medtrack.booking.domain.UserDetailsEntity;
import com.medtrack.booking.repo.AppointmentRepository;
import com.medtrack.booking.repo.DoctorClinicRepository;
import com.medtrack.booking.repo.DoctorPersonalRepository;
import com.medtrack.booking.repo.HospitalRepository;
import com.medtrack.booking.repo.UserDetailsRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Resolves hospital/doctor/fee display fields from master tables (not stored on appointments). */
@Service
public class AppointmentEnrichmentService {
  private static final String DEFAULT_CURRENCY = "INR";

  private final HospitalRepository hospitalRepo;
  private final DoctorClinicRepository clinicRepo;
  private final DoctorPersonalRepository personalRepo;
  private final AppointmentRepository appointmentRepo;
  private final UserDetailsRepository userDetailsRepo;
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  public AppointmentEnrichmentService(
      HospitalRepository hospitalRepo,
      DoctorClinicRepository clinicRepo,
      DoctorPersonalRepository personalRepo,
      AppointmentRepository appointmentRepo,
      UserDetailsRepository userDetailsRepo) {
    this.hospitalRepo = hospitalRepo;
    this.clinicRepo = clinicRepo;
    this.personalRepo = personalRepo;
    this.appointmentRepo = appointmentRepo;
    this.userDetailsRepo = userDetailsRepo;
  }

  public Long resolveHospitalId(String doctorId, Long requestHospitalId) {
    if (requestHospitalId != null) {
      return requestHospitalId;
    }
    return clinicRepo
        .findById(doctorId)
        .map(DoctorClinicEntity::getHospitalId)
        .orElse(10001L);
  }

  public String hospitalName(Long hospitalId) {
    if (hospitalId == null) {
      return null;
    }
    return hospitalRepo
        .findById(hospitalId)
        .map(h -> h.getHospitalName())
        .orElse(null);
  }

  public String doctorName(String doctorId) {
    if (doctorId == null || doctorId.isBlank()) {
      return "";
    }
    DoctorPersonalEntity personal = personalRepo.findById(doctorId).orElse(null);
    if (personal == null) {
      return doctorId;
    }
    return formatDoctorName(
        personal.getFirstName(), personal.getMiddleName(), personal.getLastName(), doctorId);
  }

  public Double consultationFee(String doctorId) {
    return clinicRepo.findById(doctorId).map(DoctorClinicEntity::getConsultationFee).orElse(null);
  }

  public Map<String, Object> enrichBooking(BookingEntity booking) {
    Map<String, Object> row = entityToMap(booking);
    Long hospitalId = booking.getHospitalId();
    String doctorId = booking.getDoctorId();
    row.put("hospitalName", hospitalName(hospitalId));
    row.put("consultationFee", consultationFee(doctorId));
    row.put("currency", DEFAULT_CURRENCY);
    return row;
  }

  public Map<String, Object> enrichAppointment(AppointmentEntity appointment) {
    Map<String, Object> row = entityToMap(appointment);
    Long hospitalId = appointment.getHospitalId();
    if (hospitalId == null) {
      hospitalId = resolveHospitalId(appointment.getDoctorId(), null);
      row.put("hospitalId", hospitalId);
    }
    String doctorId = appointment.getDoctorId();
    row.put("hospitalName", hospitalName(hospitalId));
    row.put("consultationFee", consultationFee(doctorId));
    row.put("currency", DEFAULT_CURRENCY);
    return row;
  }

  public Map<String, Object> enrichBookingSummary(BookingEntity booking) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", booking.getId());
    m.put("doctorId", booking.getDoctorId());
    m.put("doctorName", doctorName(booking.getDoctorId()));
    m.put("hospitalId", booking.getHospitalId());
    m.put("hospitalName", hospitalName(booking.getHospitalId()));
    m.put("patientName", booking.getPatientName());
    m.put("patientPhone", booking.getPatientPhone());
    m.put("patientAge", booking.getPatientAge());
    m.put("gender", booking.getGender() == null ? "" : booking.getGender());
    m.put("address", booking.getAddress() == null ? "" : booking.getAddress());
    m.put("appointmentDate", booking.getAppointmentDate().toString());
    m.put("appointmentTime", booking.getAppointmentTime().toString());
    m.put("tokenNumber", booking.getTokenNumber());
    m.put("status", booking.getStatus());
    m.put("appointmentStatus", booking.getStatus());
    m.put("reason", booking.getReason() == null ? "" : booking.getReason());
    m.put("consultationFee", consultationFee(booking.getDoctorId()));
    m.put("currency", DEFAULT_CURRENCY);
    m.put("createdAt", booking.getCreatedAt().toString());
    m.put("updatedAt", booking.getUpdatedAt().toString());

    clinicRepo
        .findById(booking.getDoctorId())
        .ifPresent(
            clinic -> {
              m.put("clinicName", nullTo(clinic.getClinicName(), ""));
              m.put("branch", nullTo(clinic.getBranch(), ""));
              String dept = clinic.getConsultationType();
              if (dept == null || dept.isBlank()) {
                dept = clinic.getClinicName();
              }
              m.put("department", nullTo(dept, ""));
            });

    appointmentRepo
        .findByBookingRefId(booking.getId())
        .ifPresent(
            appt -> {
              m.put("appointmentId", appt.getId());
              m.put("patientId", appt.getPatientId());
              if (appt.getPhoneNumber() != null && !appt.getPhoneNumber().isBlank()) {
                m.put("patientPhone", appt.getPhoneNumber());
                m.put("phoneNumber", appt.getPhoneNumber());
              }
              m.put("bookedBy", appt.getBookedBy());
              String createdByName =
                  resolveCreatedByName(
                      appt.getCreatedBy(), appt.getBookedBy(), booking.getHospitalId());
              m.put("createdBy", createdByName);
              m.put("createdByName", createdByName);
              m.put("bookedByLabel", bookedByLabel(appt, booking.getHospitalId()));
              m.put("bookedAt", appt.getCreatedDate().toString());
              if (appt.getPatientName() != null && !appt.getPatientName().isBlank()) {
                m.put("patientName", appt.getPatientName());
              }
              if (appt.getPatientAge() != null) {
                m.put("patientAge", appt.getPatientAge());
              }
              if (appt.getGender() != null && !appt.getGender().isBlank()) {
                m.put("gender", appt.getGender());
              }
              if (appt.getAddress() != null && !appt.getAddress().isBlank()) {
                m.put("address", appt.getAddress());
              }
              if (appt.getReason() != null && !appt.getReason().isBlank()) {
                m.put("reason", appt.getReason());
              }
            });
    return m;
  }

  private static String nullTo(String value, String fallback) {
    return value == null ? fallback : value;
  }

  private String bookedByLabel(AppointmentEntity appt, Long hospitalId) {
    String bookedBy = appt.getBookedBy();
    if (bookedBy == null || bookedBy.isBlank()) {
      return "Unknown";
    }
    if ("HOSPITAL".equalsIgnoreCase(bookedBy)) {
      String hosp = hospitalName(hospitalId);
      return hosp != null && !hosp.isBlank()
          ? "Hospital reception · " + hosp
          : "Hospital reception";
    }
    if ("PATIENT".equalsIgnoreCase(bookedBy)) {
      return "Self (patient portal)";
    }
    return bookedBy;
  }

  /** Resolve created_by actor id/email to a display name for patient-facing screens. */
  private String resolveCreatedByName(String createdBy, String bookedBy, Long hospitalId) {
    if (createdBy == null || createdBy.isBlank()) {
      return "HOSPITAL".equalsIgnoreCase(bookedBy) ? "Hospital staff" : "Patient";
    }
    String raw = createdBy.trim();
    String upper = raw.toUpperCase();
    if ("PATIENT".equals(upper) || "SELF".equals(upper)) {
      return "Patient";
    }
    if ("SYSTEM".equals(upper) || "HOSPITAL".equals(upper)) {
      String hosp = hospitalName(hospitalId);
      return hosp != null && !hosp.isBlank() ? hosp + " staff" : "Hospital staff";
    }
    if (upper.startsWith("DOC-") || upper.startsWith("DOC")) {
      return doctorName(raw);
    }
    if (upper.startsWith("USR")) {
      return userDetailsRepo
          .findByUserId(upper)
          .map(UserDetailsEntity::getUserName)
          .filter(n -> n != null && !n.isBlank())
          .orElse(raw);
    }
    return userDetailsRepo
        .findByUserNameIgnoreCase(raw)
        .map(UserDetailsEntity::getUserName)
        .or(
            () ->
                hospitalRepo
                    .findById(hospitalId != null ? hospitalId : -1L)
                    .filter(
                        h ->
                            raw.equalsIgnoreCase(nullTo(h.getAdminEmail(), ""))
                                || raw.equalsIgnoreCase(nullTo(h.getEmail(), "")))
                    .map(HospitalEntity::getHospitalName)
                    .map(n -> n + " admin"))
        .orElseGet(
            () -> {
              // Prefer local-part of email over raw id-looking values
              if (raw.contains("@")) {
                String local = raw.substring(0, raw.indexOf('@')).replace('.', ' ').trim();
                return local.isBlank() ? raw : capitalizeWords(local);
              }
              if (raw.matches("(?i)[0-9a-f]{8}-[0-9a-f-]{20,}")) {
                return "Hospital staff";
              }
              return capitalizeWords(raw.replace('_', ' ').replace('-', ' '));
            });
  }

  private static String capitalizeWords(String value) {
    if (value == null || value.isBlank()) return value;
    String[] parts = value.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      if (p.isBlank()) continue;
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(Character.toUpperCase(p.charAt(0)));
      if (p.length() > 1) sb.append(p.substring(1).toLowerCase());
    }
    return sb.toString();
  }

  private Map<String, Object> entityToMap(Object entity) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = mapper.convertValue(entity, Map.class);
    return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
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
}
