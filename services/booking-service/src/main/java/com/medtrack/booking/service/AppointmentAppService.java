package com.medtrack.booking.service;

import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.domain.BookingEntity;
import com.medtrack.booking.domain.DoctorProfessionalEntity;
import com.medtrack.booking.domain.PatientEntity;
import com.medtrack.booking.dto.BookingContext;
import com.medtrack.booking.dto.CreateBookingRequest;
import com.medtrack.booking.dto.RescheduleBookingRequest;
import com.medtrack.booking.repo.AppointmentRepository;
import com.medtrack.booking.repo.DoctorProfessionalRepository;
import com.medtrack.booking.repo.PatientRepository;
import com.medtrack.booking.repo.UserDetailsRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentAppService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

  private final AppointmentRepository appointmentRepo;
  private final PatientRepository patientRepo;
  private final UserDetailsRepository userDetailsRepo;
  private final DoctorProfessionalRepository professionalRepo;

  public AppointmentAppService(
      AppointmentRepository appointmentRepo,
      PatientRepository patientRepo,
      UserDetailsRepository userDetailsRepo,
      DoctorProfessionalRepository professionalRepo) {
    this.appointmentRepo = appointmentRepo;
    this.patientRepo = patientRepo;
    this.userDetailsRepo = userDetailsRepo;
    this.professionalRepo = professionalRepo;
  }

  @Transactional
  public AppointmentEntity createFromBooking(
      BookingEntity booking,
      CreateBookingRequest req,
      BookingContext ctx,
      Long hospitalId,
      String patientId) {
    try {
      AppointmentEntity row = new AppointmentEntity();
      row.setHospitalId(hospitalId);
      row.setDoctorId(booking.getDoctorId());
      row.setPatientId(patientId);
      String phone = normalizePhone(booking.getPatientPhone());
      if (phone.isBlank() && req.getPatientPhone() != null) {
        phone = normalizePhone(req.getPatientPhone());
      }
      row.setPhoneNumber(phone.isBlank() ? null : phone);
      row.setPatientName(booking.getPatientName());
      row.setPatientAge(booking.getPatientAge());
      row.setGender(booking.getGender());
      row.setAddress(booking.getAddress());
      row.setReason(booking.getReason());
      row.setAppointmentDate(booking.getAppointmentDate());
      row.setAppointmentTime(booking.getAppointmentTime());
      row.setTokenNumber(booking.getTokenNumber());
      row.setStatus(booking.getStatus());
      row.setPaymentStatus("UNPAID");
      row.setBookedBy(ctx.bookedBy());
      row.setBookingRefId(booking.getId());
      row.setCreatedBy(ctx.createdBy());
      row.setUpdatedBy(ctx.updatedBy());
      row.setDepartment(doctorDepartment(booking.getDoctorId()));
      return appointmentRepo.save(row);
    } catch (Exception ex) {
      System.err.printf(
          "[appointment] insert failed (booking still saved): %s%n", ex.getMessage());
      return null;
    }
  }

  @Transactional
  public void syncFromBooking(BookingEntity booking, String updatedBy) {
    appointmentRepo
        .findByBookingRefId(booking.getId())
        .ifPresent(
            appt -> {
              appt.setStatus(booking.getStatus());
              appt.setAppointmentDate(booking.getAppointmentDate());
              appt.setAppointmentTime(booking.getAppointmentTime());
              appt.setReason(booking.getReason());
              appt.setTokenNumber(booking.getTokenNumber());
              String phone = normalizePhone(booking.getPatientPhone());
              if (!phone.isBlank()) {
                appt.setPhoneNumber(phone);
              }
              appt.setUpdatedBy(updatedBy != null ? updatedBy : "SYSTEM");
              appointmentRepo.save(appt);
            });
  }

  @Transactional
  public AppointmentEntity reschedule(
      BookingEntity booking, RescheduleBookingRequest req, String updatedBy) {
    AppointmentEntity appt =
        appointmentRepo
            .findByBookingRefId(booking.getId())
            .orElseThrow();
    appt.setAppointmentDate(req.getAppointmentDate());
    appt.setAppointmentTime(req.getAppointmentTime());
    if (req.getReason() != null && !req.getReason().isBlank()) {
      appt.setReason(req.getReason());
    }
    appt.setStatus("BOOKED");
    appt.setUpdatedBy(updatedBy != null ? updatedBy : "PATIENT");
    return appointmentRepo.save(appt);
  }

  public Optional<AppointmentEntity> findByBookingRef(String bookingRefId) {
    return appointmentRepo.findByBookingRefId(bookingRefId);
  }

  /** Payment and appointment workflow stay separate. Queue status remains BOOKED. */
  @Transactional
  public void markPaid(String appointmentId, String paymentId) {
    if (appointmentId == null || appointmentId.isBlank()) return;
    appointmentRepo
        .findById(appointmentId)
        .ifPresent(
            appt -> {
              if ("PAID".equalsIgnoreCase(appt.getPaymentStatus())) {
                return;
              }
              appt.setPaymentStatus("PAID");
              if (paymentId != null && !paymentId.isBlank()) {
                appt.setPaymentId(paymentId);
              }
              appt.setUpdatedBy("PAYMENT");
              appointmentRepo.save(appt);
            });
  }

  public List<AppointmentEntity> findAll() {
    return appointmentRepo.findAll().stream()
        .sorted((a, b) -> b.getCreatedDate().compareTo(a.getCreatedDate()))
        .toList();
  }

  public List<AppointmentEntity> findByPatientId(String phoneOrPatientId) {
    List<AppointmentEntity> merged = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (String key : lookupKeys(phoneOrPatientId)) {
      for (AppointmentEntity row :
          appointmentRepo.findByPatientIdOrderByCreatedDateDesc(key)) {
        if (seen.add(row.getId())) {
          merged.add(row);
        }
      }
    }
    return merged;
  }

  public List<AppointmentEntity> findByDoctor(String doctorId, LocalDate date) {
    if (date != null) {
      return appointmentRepo.findByDoctorIdAndAppointmentDateOrderByAppointmentTimeAsc(
          doctorId, date);
    }
    return appointmentRepo.findByDoctorIdOrderByAppointmentTimeAsc(doctorId);
  }

  public List<AppointmentEntity> findByHospital(Long hospitalId) {
    return appointmentRepo.findByHospitalIdOrderByCreatedDateDesc(hospitalId);
  }

  public Optional<AppointmentEntity> findTodayActiveByPatientId(String phoneOrPatientId) {
    LocalDate today = LocalDate.now(ZONE);
    String normalized = normalizePhone(phoneOrPatientId);
    if (!normalized.isBlank()) {
      Optional<AppointmentEntity> byPhone =
          pickTodayActive(
              appointmentRepo.findByPhoneNumberOrderByCreatedDateDesc(normalized), today);
      if (byPhone.isPresent()) {
        return byPhone;
      }
    }
    for (String key : lookupKeys(phoneOrPatientId)) {
      Optional<AppointmentEntity> match =
          pickTodayActive(appointmentRepo.findByPatientIdOrderByCreatedDateDesc(key), today);
      if (match.isPresent()) {
        return match;
      }
    }
    return Optional.empty();
  }

  private static Optional<AppointmentEntity> pickTodayActive(
      List<AppointmentEntity> rows, LocalDate today) {
    return rows.stream()
        .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().equals(today))
        .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getStatus()))
        .sorted(
            (a, b) -> {
              if (a.getAppointmentTime() == null && b.getAppointmentTime() == null) return 0;
              if (a.getAppointmentTime() == null) return 1;
              if (b.getAppointmentTime() == null) return -1;
              return a.getAppointmentTime().compareTo(b.getAppointmentTime());
            })
        .findFirst();
  }

  public long countTokensForDoctorDate(String doctorId, LocalDate date) {
    return appointmentRepo.countByDoctorIdAndAppointmentDateAndStatusNot(
        doctorId, date, "CANCELLED");
  }

  List<String> lookupKeys(String phoneOrPatientId) {
    List<String> keys = new ArrayList<>();
    if (phoneOrPatientId != null && !phoneOrPatientId.isBlank()) {
      String raw = phoneOrPatientId.trim();
      keys.add(raw.toUpperCase().startsWith("USR") ? raw.toUpperCase() : raw);
    }
    String normalized = normalizePhone(phoneOrPatientId);
    if (!normalized.isBlank() && !keys.contains(normalized)) {
      keys.add(normalized);
    }
    if (phoneOrPatientId != null && phoneOrPatientId.trim().toUpperCase().startsWith("USR")) {
      userDetailsRepo
          .findByUserId(phoneOrPatientId.trim().toUpperCase())
          .map(u -> normalizePhone(u.getPhone()))
          .filter(p -> !p.isBlank())
          .ifPresent(
              p -> {
                if (!keys.contains(p)) {
                  keys.add(p);
                }
              });
    }
    patientRepo
        .findByPhone(normalized)
        .map(PatientEntity::getId)
        .ifPresent(id -> keys.add(0, id));
    return keys;
  }

  static String normalizePhone(String raw) {
    if (raw == null) return "";
    return raw.replaceAll("\\D", "");
  }

  private String doctorDepartment(String doctorId) {
    if (doctorId == null || doctorId.isBlank()) return null;
    return professionalRepo
        .findById(doctorId)
        .map(DoctorProfessionalEntity::getDepartment)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .orElse(null);
  }
}
