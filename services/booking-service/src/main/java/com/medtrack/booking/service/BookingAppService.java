package com.medtrack.booking.service;

import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.domain.BookingEntity;
import com.medtrack.booking.domain.PatientEntity;
import com.medtrack.booking.dto.BookingContext;
import com.medtrack.booking.dto.CreateBookingRequest;
import com.medtrack.booking.dto.RescheduleBookingRequest;
import com.medtrack.booking.event.BookingCreatedEvent;
import com.medtrack.booking.repo.AppointmentRepository;
import com.medtrack.booking.repo.BookingRepository;
import com.medtrack.booking.repo.PatientRepository;
import com.medtrack.common.port.DoctorBusyPort;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingAppService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

  private final BookingRepository bookingRepo;
  private final PatientRepository patientRepo;
  private final AppointmentRepository appointmentRepo;
  private final AppointmentAppService appointmentService;
  private final AppointmentEnrichmentService enrichment;
  private final UserRegistrationService userRegistrationService;
  private final ApplicationEventPublisher events;
  private final DoctorBusyPort doctorBusyPort;

  public BookingAppService(
      BookingRepository bookingRepo,
      PatientRepository patientRepo,
      AppointmentRepository appointmentRepo,
      AppointmentAppService appointmentService,
      AppointmentEnrichmentService enrichment,
      UserRegistrationService userRegistrationService,
      ApplicationEventPublisher events,
      @Lazy DoctorBusyPort doctorBusyPort) {
    this.bookingRepo = bookingRepo;
    this.patientRepo = patientRepo;
    this.appointmentRepo = appointmentRepo;
    this.appointmentService = appointmentService;
    this.enrichment = enrichment;
    this.userRegistrationService = userRegistrationService;
    this.events = events;
    this.doctorBusyPort = doctorBusyPort;
  }

  /** GET — fetch all bookings (newest first). */
  public List<BookingEntity> findAll() {
    return bookingRepo.findAll().stream()
        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
        .toList();
  }

  /** GET — fetch one booking by id. */
  public BookingEntity findById(String id) {
    return bookingRepo
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + id));
  }

  /** GET — fetch bookings for a doctor (desk view). */
  public List<BookingEntity> findByDoctor(String doctorId, LocalDate date) {
    if (date != null) {
      return bookingRepo.findByDoctorIdAndAppointmentDateOrderByAppointmentTimeAsc(doctorId, date);
    }
    return bookingRepo.findByDoctorIdOrderByAppointmentTimeAsc(doctorId);
  }

  /** GET — fetch bookings for a hospital. */
  public List<BookingEntity> findByHospital(Long hospitalId) {
    return bookingRepo.findByHospitalIdOrderByAppointmentTimeDesc(hospitalId);
  }

  /** GET — fetch bookings by patient phone. */
  public List<BookingEntity> findByPhone(String phone) {
    return bookingRepo.findByPatientPhoneOrderByCreatedAtDesc(phone.replaceAll("\\D", ""));
  }

  /** GET — upcoming bookings for a patient (not cancelled, today or future). */
  public List<BookingEntity> findUpcomingByPhone(String phone) {
    return findUpcoming(phone, null);
  }

  /** GET — all upcoming bookings by phone and/or patient UUID, sorted by appointment date/time. */
  public List<BookingEntity> findUpcoming(String phone, String patientId) {
    LocalDate today = LocalDate.now(ZONE);
    Set<String> seen = new LinkedHashSet<>();
    List<BookingEntity> merged = new ArrayList<>();

    String normalized = phone == null ? "" : phone.replaceAll("\\D", "");
    if (!normalized.isBlank()) {
      for (BookingEntity b : bookingRepo.findByPatientPhoneOrderByCreatedAtDesc(normalized)) {
        if (isUpcoming(b, today) && seen.add(b.getId())) {
          merged.add(b);
        }
      }
      // Hospital bookings tracked by appointments.phone_number
      for (AppointmentEntity appt :
          appointmentRepo.findByPhoneNumberOrderByCreatedDateDesc(normalized)) {
        addUpcomingFromAppointment(appt, today, seen, merged);
      }
    }

    Set<String> patientKeys = new LinkedHashSet<>();
    String resolvedPatientId = resolvePatientIdKey(normalized, patientId);
    if (resolvedPatientId != null && !resolvedPatientId.isBlank()) {
      patientKeys.add(resolvedPatientId);
    }
    if (patientId != null && !patientId.isBlank()) {
      patientKeys.addAll(appointmentService.lookupKeys(patientId.trim()));
    }
    if (!normalized.isBlank()) {
      patientKeys.addAll(appointmentService.lookupKeys(normalized));
    }

    for (String key : patientKeys) {
      for (AppointmentEntity appt : appointmentRepo.findByPatientIdOrderByCreatedDateDesc(key)) {
        addUpcomingFromAppointment(appt, today, seen, merged);
      }
    }

    merged.sort(
        Comparator.comparing(BookingEntity::getAppointmentDate)
            .thenComparing(BookingEntity::getAppointmentTime));
    return merged;
  }

  private void addUpcomingFromAppointment(
      AppointmentEntity appt,
      LocalDate today,
      Set<String> seen,
      List<BookingEntity> merged) {
    if (appt.getBookingRefId() == null || appt.getBookingRefId().isBlank()) {
      return;
    }
    if ("CANCELLED".equalsIgnoreCase(appt.getStatus())) {
      return;
    }
    bookingRepo
        .findById(appt.getBookingRefId())
        .ifPresent(
            b -> {
              if (isUpcoming(b, today) && seen.add(b.getId())) {
                merged.add(b);
              }
            });
  }

  private static boolean isUpcoming(BookingEntity b, LocalDate today) {
    return !"CANCELLED".equalsIgnoreCase(b.getStatus())
        && !b.getAppointmentDate().isBefore(today);
  }

  private String resolvePatientIdKey(String normalizedPhone, String patientId) {
    if (patientId != null && !patientId.isBlank()) {
      String id = patientId.trim();
      if (id.toUpperCase().startsWith("USR")) {
        return id.toUpperCase();
      }
      return id;
    }
    if (!normalizedPhone.isBlank()) {
      return patientRepo.findByPhone(normalizedPhone).map(PatientEntity::getId).orElse(null);
    }
    return null;
  }

  /** GET — all bookings for a patient (history). */
  public List<BookingEntity> findHistoryByPhone(String phone) {
    return bookingRepo.findByPatientPhoneOrderByCreatedAtDesc(phone.replaceAll("\\D", ""));
  }

  /** POST — cancel a booking (patient must match phone). */
  @Transactional
  public BookingEntity cancel(String id, String patientPhone) {
    BookingEntity b = findById(id);
    String normalized = patientPhone.replaceAll("\\D", "");
    if (!b.getPatientPhone().equals(normalized)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your booking");
    }
    if ("CANCELLED".equalsIgnoreCase(b.getStatus())) {
      return b;
    }
    if ("COMPLETED".equalsIgnoreCase(b.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel completed booking");
    }
    b.setStatus("CANCELLED");
    BookingEntity saved = bookingRepo.save(b);
    appointmentService.syncFromBooking(saved, "PATIENT");
    return saved;
  }

  /** PATCH — reschedule a booking. */
  @Transactional
  public BookingEntity reschedule(String id, String patientPhone, RescheduleBookingRequest req) {
    BookingEntity b = findById(id);
    String normalized = patientPhone.replaceAll("\\D", "");
    if (!b.getPatientPhone().equals(normalized)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your booking");
    }
    if ("CANCELLED".equalsIgnoreCase(b.getStatus()) || "COMPLETED".equalsIgnoreCase(b.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot reschedule this booking");
    }
    b.setAppointmentDate(req.getAppointmentDate());
    b.setAppointmentTime(req.getAppointmentTime());
    if (req.getReason() != null && !req.getReason().isBlank()) {
      b.setReason(req.getReason());
    }
    b.setStatus("BOOKED");
    BookingEntity saved = bookingRepo.save(b);
    appointmentService.reschedule(saved, req, "PATIENT");
    return saved;
  }

  private Optional<BookingEntity> bookingFromAppointment(AppointmentEntity a) {
    if (a.getBookingRefId() == null || a.getBookingRefId().isBlank()) {
      return Optional.empty();
    }
    return bookingRepo.findById(a.getBookingRefId());
  }

  /** Find today's active booking for queue tracking. */
  public Optional<BookingEntity> findTodayActiveByPhone(String phone) {
    return findTodayActive(phone, null);
  }

  /** Find today's booking by patient UUID / User ID and/or phone. */
  public Optional<BookingEntity> findTodayActive(String phone, String patientId) {
    if (patientId != null && !patientId.isBlank()) {
      Optional<BookingEntity> byPatient =
          appointmentService
              .findTodayActiveByPatientId(patientId.trim())
              .flatMap(this::bookingFromAppointment);
      if (byPatient.isPresent()) {
        return byPatient;
      }
    }
    if (phone != null && !phone.isBlank()) {
      Optional<BookingEntity> fromAppointment =
          appointmentService
              .findTodayActiveByPatientId(phone)
              .flatMap(this::bookingFromAppointment);
      if (fromAppointment.isPresent()) {
        return fromAppointment;
      }
    }
    String normalized = phone == null ? "" : phone.replaceAll("\\D", "");
    if (normalized.isBlank()) {
      return Optional.empty();
    }
    LocalDate today = LocalDate.now(ZONE);
    return bookingRepo.findByPatientPhoneOrderByCreatedAtDesc(normalized).stream()
        .filter(b -> b.getAppointmentDate().equals(today))
        .filter(b -> !"CANCELLED".equalsIgnoreCase(b.getStatus()))
        .sorted(
            (a, b) -> {
              if (a.getAppointmentTime() == null && b.getAppointmentTime() == null) return 0;
              if (a.getAppointmentTime() == null) return 1;
              if (b.getAppointmentTime() == null) return -1;
              return a.getAppointmentTime().compareTo(b.getAppointmentTime());
            })
        .findFirst();
  }

  @Transactional
  public BookingEntity create(CreateBookingRequest req) {
    return create(req, BookingContext.fromRequest(req, null, null));
  }

  @Transactional
  public BookingEntity create(CreateBookingRequest req, BookingContext ctx) {
    String phone = req.getPatientPhone().replaceAll("\\D", "");
    if (phone.length() < 8) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid patientPhone");
    }
    if (doctorBusyPort != null
        && req.getDoctorId() != null
        && req.getAppointmentDate() != null
        && doctorBusyPort.isBusyOnDate(req.getDoctorId(), req.getAppointmentDate())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Doctor is busy on " + req.getAppointmentDate() + ". Please choose another date.");
    }

    long existing = 0;
    try {
      existing =
          appointmentService.countTokensForDoctorDate(
              req.getDoctorId(), req.getAppointmentDate());
    } catch (Exception ex) {
      System.err.printf(
          "[booking] appointment token count failed, using bookings table: %s%n",
          ex.getMessage());
    }
    if (existing == 0) {
      existing =
          bookingRepo.countByDoctorIdAndAppointmentDateAndStatusNot(
              req.getDoctorId(), req.getAppointmentDate(), "CANCELLED");
    }
    int token = (int) existing + 1;

    BookingEntity b = new BookingEntity();
    Long hospitalId =
        enrichment.resolveHospitalId(
            req.getDoctorId(), ctx.hospitalId() != null ? ctx.hospitalId() : req.getHospitalId());
    b.setHospitalId(hospitalId);
    b.setDoctorId(req.getDoctorId());
    b.setPatientName(req.getPatientName().trim());
    b.setPatientPhone(phone);
    b.setPatientAge(req.getPatientAge());
    b.setGender(req.getGender());
    b.setAddress(req.getAddress());
    b.setReason(req.getReason());
    b.setAppointmentDate(req.getAppointmentDate());
    b.setAppointmentTime(req.getAppointmentTime());
    b.setTokenNumber(token);
    b.setStatus("BOOKED");

    BookingEntity saved = bookingRepo.save(b);
    String patientId = resolvePatientId(req);
    appointmentService.createFromBooking(saved, req, ctx, hospitalId, patientId);
    persistPatientEmail(phone, req.getPatientEmail(), req.getPatientName());
    events.publishEvent(new BookingCreatedEvent(saved, req.getPatientEmail()));
    System.out.printf(
        "[appointment] INSERT id=%s hospital=%s doctor=%s patientId=%s token=%d%n",
        saved.getId(),
        hospitalId,
        saved.getDoctorId(),
        patientId,
        saved.getTokenNumber());
    return saved;
  }

  /** Keep email on patients + user_details so later bookings / profile can reuse it. */
  private void persistPatientEmail(String phone, String email, String patientName) {
    if (email == null || email.isBlank()) {
      return;
    }
    String normalizedEmail = email.trim().toLowerCase();
    userRegistrationService.upsertEmailByPhone(phone, normalizedEmail);
    patientRepo
        .findByPhone(phone)
        .ifPresentOrElse(
            p -> {
              p.setEmail(normalizedEmail);
              if ((p.getName() == null || p.getName().isBlank())
                  && patientName != null
                  && !patientName.isBlank()) {
                p.setName(patientName.trim());
              }
              patientRepo.save(p);
            },
            () -> {
              // No legacy patients row — still fine; user_details email is enough for notify.
            });
  }

  /** Resolve appointments.patient_id — prefer logged-in User ID (USR…), then patient UUID, then phone. */
  String resolvePatientId(CreateBookingRequest req) {
    String requested = req.getPatientId();
    if (requested != null && !requested.isBlank()) {
      String id = requested.trim();
      if (id.toUpperCase().startsWith("USR")) {
        return id.toUpperCase();
      }
      if (isPatientUuid(id) && patientRepo.findById(id).isPresent()) {
        return id;
      }
    }
    String phone = req.getPatientPhone().replaceAll("\\D", "");
    Optional<PatientEntity> registered = patientRepo.findByPhone(phone);
    if (registered.isPresent()) {
      return registered.get().getId();
    }
    return phone;
  }

  private static boolean isPatientUuid(String id) {
    return id != null
        && !id.isBlank()
        && !id.startsWith("USR")
        && !id.startsWith("DOC-");
  }
}
