package com.medtrack.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.booking.domain.DoctorClinicEntity;
import com.medtrack.booking.domain.DoctorContactEntity;
import com.medtrack.booking.domain.DoctorPersonalEntity;
import com.medtrack.booking.domain.DoctorProfessionalEntity;
import com.medtrack.booking.domain.EmergencyBedBookingEntity;
import com.medtrack.booking.domain.EmergencyBedEntity;
import com.medtrack.booking.domain.HospitalEntity;
import com.medtrack.booking.domain.LoginEntity;
import com.medtrack.booking.domain.MedicalStoreEntity;
import com.medtrack.booking.domain.PatientEntity;
import com.medtrack.booking.repo.AppointmentRepository;
import com.medtrack.booking.repo.DoctorClinicRepository;
import com.medtrack.booking.repo.DoctorContactRepository;
import com.medtrack.booking.repo.DoctorPersonalRepository;
import com.medtrack.booking.repo.DoctorProfessionalRepository;
import com.medtrack.booking.repo.EmergencyBedBookingRepository;
import com.medtrack.booking.repo.EmergencyBedRepository;
import com.medtrack.booking.repo.HospitalRepository;
import com.medtrack.booking.repo.LoginRepository;
import com.medtrack.booking.repo.MedicalStoreRepository;
import com.medtrack.booking.repo.PatientRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmergencyAppService {
  public static final String AVAILABLE = "AVAILABLE";
  public static final String BOOKED = "BOOKED";
  public static final String UNPAID = "UNPAID";
  public static final String PAYMENT_PENDING = "PAYMENT_PENDING";
  public static final String PAID = "PAID";
  public static final String SLIP_ATTACHED = "SLIP_ATTACHED";
  public static final String EMERGENCY_BED = "EMERGENCY_BED";

  private final HospitalRepository hospitalRepo;
  private final EmergencyBedRepository bedRepo;
  private final EmergencyBedBookingRepository bookingRepo;
  private final DoctorClinicRepository clinicRepo;
  private final DoctorPersonalRepository personalRepo;
  private final DoctorProfessionalRepository professionalRepo;
  private final DoctorContactRepository contactRepo;
  private final LoginRepository loginRepo;
  private final MedicalStoreRepository storeRepo;
  private final PatientRepository patientRepo;
  private final AppointmentRepository appointmentRepo;
  private final PaymentAppService payments;
  private final ObjectMapper mapper;
  private final Path slipRoot;

  public EmergencyAppService(
      HospitalRepository hospitalRepo,
      EmergencyBedRepository bedRepo,
      EmergencyBedBookingRepository bookingRepo,
      DoctorClinicRepository clinicRepo,
      DoctorPersonalRepository personalRepo,
      DoctorProfessionalRepository professionalRepo,
      DoctorContactRepository contactRepo,
      LoginRepository loginRepo,
      MedicalStoreRepository storeRepo,
      PatientRepository patientRepo,
      AppointmentRepository appointmentRepo,
      PaymentAppService payments,
      ObjectMapper mapper,
      @Value("${medtrack.upload-dir:uploads/patient-documents}") String uploadDir) {
    this.hospitalRepo = hospitalRepo;
    this.bedRepo = bedRepo;
    this.bookingRepo = bookingRepo;
    this.clinicRepo = clinicRepo;
    this.personalRepo = personalRepo;
    this.professionalRepo = professionalRepo;
    this.contactRepo = contactRepo;
    this.loginRepo = loginRepo;
    this.storeRepo = storeRepo;
    this.patientRepo = patientRepo;
    this.appointmentRepo = appointmentRepo;
    this.payments = payments;
    this.mapper = mapper;
    this.slipRoot = Paths.get(uploadDir).toAbsolutePath().getParent().resolve("emergency-slips");
  }

  public Map<String, Object> context(
      String loginType,
      Long hospitalId,
      String doctorId,
      String storeId,
      String patientPhone,
      String patientId,
      String username) {
    String state = "";
    String city = "";
    String patientName = "";
    String phone = firstNonBlank(patientPhone);

    if (hospitalId != null) {
      HospitalEntity hospital = hospitalRepo.findById(hospitalId).orElse(null);
      if (hospital != null) {
        state = firstNonBlank(hospital.getState());
        city = firstNonBlank(hospital.getCity());
      }
    }

    if ("USER".equalsIgnoreCase(loginType) && !blank(doctorId)) {
      DoctorContactEntity contact = contactRepo.findById(doctorId).orElse(null);
      if (contact != null) {
        state = firstNonBlank(contact.getState(), state);
        city = firstNonBlank(contact.getCity(), city);
      }
      DoctorPersonalEntity personal = personalRepo.findById(doctorId).orElse(null);
      if (personal != null) {
        patientName = formatName(personal.getFirstName(), personal.getLastName());
      }
    } else if ("MEDICAL".equalsIgnoreCase(loginType) && !blank(storeId)) {
      MedicalStoreEntity store = storeRepo.findById(storeId).orElse(null);
      if (store != null) {
        state = firstNonBlank(store.getState(), state);
        city = firstNonBlank(store.getCity(), city);
      }
    } else if ("PATIENT".equalsIgnoreCase(loginType)) {
      PatientEntity patient = null;
      if (!blank(patientPhone)) patient = patientRepo.findByPhone(patientPhone).orElse(null);
      if (patient == null && !blank(patientId)) patient = patientRepo.findById(patientId).orElse(null);
      if (patient != null) {
        patientName = firstNonBlank(patient.getName());
        phone = firstNonBlank(patient.getPhone(), phone);
        if (blank(city) && blank(state)) {
          var last =
              appointmentRepo.findByPatientIdOrderByCreatedDateDesc(patient.getId()).stream()
                  .findFirst()
                  .orElse(null);
          if (last == null && !blank(patient.getPhone())) {
            last =
                appointmentRepo.findByPhoneNumberOrderByCreatedDateDesc(patient.getPhone()).stream()
                    .findFirst()
                    .orElse(null);
          }
          if (last != null && last.getHospitalId() != null) {
            HospitalEntity hospital = hospitalRepo.findById(last.getHospitalId()).orElse(null);
            if (hospital != null) {
              state = firstNonBlank(hospital.getState());
              city = firstNonBlank(hospital.getCity());
            }
          }
        }
      }
    } else if ("HOSPITAL".equalsIgnoreCase(loginType) && hospitalId != null) {
      LoginEntity login =
          loginRepo.findByLoginTypeAndHospitalId("HOSPITAL", hospitalId).orElse(null);
      if (login != null) patientName = firstNonBlank(login.getDisplayName());
    }

    if (blank(patientName)) patientName = firstNonBlank(username);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("state", state);
    out.put("city", city);
    out.put("patientName", patientName);
    out.put("patientPhone", phone);
    out.put("loginType", firstNonBlank(loginType));
    return out;
  }

  @Transactional
  public Map<String, Object> searchHospitals(String state, String city, String q) {
    String st = norm(state);
    String ct = norm(city);
    String query = norm(q);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (HospitalEntity hospital : hospitalRepo.findAll()) {
      if (!statusAllowsSearch(hospital.getStatus())) continue;
      if (!st.isEmpty() && !norm(hospital.getState()).equals(st)) continue;
      if (!ct.isEmpty() && !norm(hospital.getCity()).equals(ct)) continue;
      if (!query.isEmpty()) {
        String hay =
            (firstNonBlank(hospital.getHospitalName())
                    + " "
                    + hospital.getId()
                    + " "
                    + firstNonBlank(hospital.getHospitalCode()))
                .toLowerCase(Locale.ROOT);
        if (!hay.contains(query)) continue;
      }
      ensureBeds(hospital);
      rows.add(toHospitalCard(hospital, false));
    }
    rows.sort(
        (a, b) ->
            String.valueOf(a.get("hospitalName"))
                .compareToIgnoreCase(String.valueOf(b.get("hospitalName"))));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("count", rows.size());
    out.put("state", state == null ? "" : state);
    out.put("city", city == null ? "" : city);
    out.put("hospitals", rows);
    return out;
  }

  @Transactional
  public Map<String, Object> hospitalDetail(Long hospitalId) {
    HospitalEntity hospital =
        hospitalRepo
            .findById(hospitalId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found"));
    ensureBeds(hospital);
    Map<String, Object> out = toHospitalCard(hospital, true);
    out.put("beds", bedRepo.findByHospitalIdOrderByBedNumberAsc(hospitalId).stream().map(this::toBed).toList());
    out.put("doctors", emergencyDoctors(hospitalId));
    out.put("email", hospital.getEmail());
    out.put("primaryContact", hospital.getPrimaryContact());
    out.put("hospitalType", hospital.getHospitalType());
    out.put("country", hospital.getCountry());
    return out;
  }

  @Transactional
  public Map<String, Object> book(String bedId, Map<String, Object> body, Actor actor) {
    EmergencyBedEntity bed =
        bedRepo
            .findById(bedId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bed not found"));
    if (!AVAILABLE.equalsIgnoreCase(bed.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This bed is already booked");
    }
    String patientName = firstNonBlank(str(body, "patientName"), actor.patientName);
    if (patientName.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient name is required");
    }
    String phone = firstNonBlank(str(body, "patientPhone"), actor.patientPhone);
    String patientId = firstNonBlank(str(body, "patientId"), actor.patientId, phone);

    EmergencyBedBookingEntity booking = new EmergencyBedBookingEntity();
    booking.setId("ebb-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    booking.setHospitalId(bed.getHospitalId());
    booking.setBedId(bed.getId());
    booking.setBedNumber(bed.getBedNumber());
    booking.setPatientName(patientName.trim());
    booking.setPatientPhone(phone);
    booking.setPatientId(patientId);
    booking.setBookedByLoginId(firstNonBlank(actor.username, actor.patientId));
    booking.setBookedByType(firstNonBlank(actor.loginType, "USER"));
    booking.setStatus(BOOKED);
    booking.setFees(bed.getFees());
    booking.setPaymentStatus(UNPAID);
    booking.setNotes(str(body, "notes"));
    booking.setCreatedAt(Instant.now());
    booking = bookingRepo.save(booking);

    bed.setStatus(BOOKED);
    bedRepo.save(bed);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("message", "Emergency bed " + bed.getBedNumber() + " booked");
    out.put("booking", toBooking(booking));
    out.put("bed", toBed(bed));
    return out;
  }

  @Transactional
  public Map<String, Object> createPayment(String bookingId) {
    EmergencyBedBookingEntity booking = requireBooking(bookingId);
    if (PAID.equalsIgnoreCase(booking.getPaymentStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This bed is already paid");
    }
    HospitalEntity hospital = hospitalRepo.findById(booking.getHospitalId()).orElse(null);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("referenceType", EMERGENCY_BED);
    body.put("referenceId", booking.getId());
    body.put("bookingId", booking.getId());
    body.put("amount", booking.getFees() == null ? 2500d : booking.getFees());
    body.put("patientId", firstNonBlank(booking.getPatientId(), booking.getPatientPhone(), booking.getId()));
    body.put("hospitalName", hospital == null ? "MedTrack Emergency" : hospital.getHospitalName());
    Map<String, Object> payment = payments.create(body);
    String paymentId = String.valueOf(payment.getOrDefault("paymentId", ""));
    if (!blank(paymentId)) {
      booking.setPaymentId(paymentId);
      booking.setPaymentStatus(PAYMENT_PENDING);
      booking.setUpdatedAt(Instant.now());
      bookingRepo.save(booking);
    }
    Map<String, Object> out = new LinkedHashMap<>(payment);
    out.put("booking", toBooking(booking));
    return out;
  }

  @Transactional
  public void markPaid(String bookingId, String paymentId) {
    if (blank(bookingId)) return;
    bookingRepo
        .findById(bookingId)
        .ifPresent(
            booking -> {
              if (PAID.equalsIgnoreCase(booking.getPaymentStatus())) return;
              booking.setPaymentStatus(PAID);
              if (!blank(paymentId)) booking.setPaymentId(paymentId);
              booking.setUpdatedAt(Instant.now());
              bookingRepo.save(booking);
            });
    if (!blank(paymentId)) {
      bookingRepo
          .findByPaymentId(paymentId)
          .ifPresent(
              booking -> {
                if (PAID.equalsIgnoreCase(booking.getPaymentStatus())) return;
                booking.setPaymentStatus(PAID);
                booking.setUpdatedAt(Instant.now());
                bookingRepo.save(booking);
              });
    }
  }

  @Transactional
  public Map<String, Object> attachSlip(String bookingId, MultipartFile file, Actor actor) {
    EmergencyBedBookingEntity booking = requireBooking(bookingId);
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment slip file is required");
    }
    if (file.getSize() > 8L * 1024 * 1024) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be 8 MB or smaller");
    }
    try {
      Path destDir = slipRoot.resolve(booking.getId());
      Files.createDirectories(destDir);
      String original = safeName(file.getOriginalFilename());
      Path target = destDir.resolve(UUID.randomUUID() + "_" + original);
      Files.write(target, file.getBytes());
      booking.setPaymentSlipPath(target.toString());
      booking.setPaymentSlipName(original);
      if (!PAID.equalsIgnoreCase(booking.getPaymentStatus())) {
        booking.setPaymentStatus(SLIP_ATTACHED);
      }
      booking.setUpdatedAt(Instant.now());
      bookingRepo.save(booking);
    } catch (IOException ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save payment slip");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("message", "Payment slip attached (optional proof saved)");
    out.put("booking", toBooking(booking));
    return out;
  }

  public Map<String, Object> getBooking(String bookingId) {
    return Map.of("booking", toBooking(requireBooking(bookingId)));
  }

  @Transactional
  public void ensureBeds(HospitalEntity hospital) {
    if (hospital == null || hospital.getId() == null) return;
    if (bedRepo.countByHospitalId(hospital.getId()) > 0) return;
    int total = emergencyBedCount(hospital);
    Instant now = Instant.now();
    for (int i = 1; i <= total; i++) {
      EmergencyBedEntity bed = new EmergencyBedEntity();
      bed.setId("ebed-" + hospital.getId() + "-" + String.format("%02d", i));
      bed.setHospitalId(hospital.getId());
      boolean icu = i % 4 == 0;
      bed.setBedNumber((icu ? "ICU-" : "ER-") + String.format("%02d", i));
      bed.setWard(icu ? "ICU" : "EMERGENCY");
      bed.setFees(icu ? 5000d : 2500d);
      bed.setStatus(AVAILABLE);
      bed.setNotes(icu ? "ICU emergency bed" : "Emergency ward bed");
      bed.setCreatedAt(now);
      bedRepo.save(bed);
    }
  }

  private List<Map<String, Object>> emergencyDoctors(Long hospitalId) {
    List<DoctorClinicEntity> clinics = clinicRepo.findByHospitalIdOrderByHospitalNameAsc(hospitalId);
    List<Map<String, Object>> emergency = new ArrayList<>();
    List<Map<String, Object>> others = new ArrayList<>();
    String today = LocalDate.now().getDayOfWeek().name();
    for (DoctorClinicEntity clinic : clinics) {
      Map<String, Object> row = toDoctor(clinic, today);
      if (Boolean.TRUE.equals(row.get("emergencyDoctor"))) emergency.add(row);
      else others.add(row);
    }
    if (!emergency.isEmpty()) return emergency;
    return others;
  }

  private Map<String, Object> toDoctor(DoctorClinicEntity clinic, String today) {
    String doctorId = clinic.getDoctorId();
    DoctorPersonalEntity personal = personalRepo.findById(doctorId).orElse(null);
    DoctorProfessionalEntity professional = professionalRepo.findById(doctorId).orElse(null);
    DoctorContactEntity contact = contactRepo.findById(doctorId).orElse(null);
    String department = professional == null ? "" : firstNonBlank(professional.getDepartment());
    String specialization =
        professional == null ? "" : firstNonBlank(professional.getSpecialization());
    boolean emergency =
        containsEmergency(department) || containsEmergency(specialization);
    boolean available = isAvailableToday(clinic.getAvailableDays(), today);
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("doctorId", doctorId);
    row.put(
        "doctorName",
        formatDoctorName(
            personal == null ? null : personal.getFirstName(),
            personal == null ? null : personal.getLastName(),
            doctorId));
    row.put("department", department);
    row.put("specialization", specialization);
    row.put("mobileNumber", contact == null ? null : contact.getMobileNumber());
    row.put("availableDays", clinic.getAvailableDays());
    row.put("availableTimeSlots", clinic.getAvailableTimeSlots());
    row.put("availableNow", available);
    row.put("emergencyDoctor", emergency);
    return row;
  }

  private Map<String, Object> toHospitalCard(HospitalEntity hospital, boolean withDoctors) {
    long total = bedRepo.countByHospitalId(hospital.getId());
    long booked = bedRepo.countByHospitalIdAndStatus(hospital.getId(), BOOKED);
    long available = Math.max(0, total - booked);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("hospitalId", hospital.getId());
    m.put("id", hospital.getId());
    m.put("hospitalName", hospital.getHospitalName());
    m.put("hospitalType", hospital.getHospitalType());
    m.put("city", hospital.getCity());
    m.put("state", hospital.getState());
    m.put("primaryContact", hospital.getPrimaryContact());
    m.put("status", hospital.getStatus());
    m.put("totalBeds", total);
    m.put("bookedBeds", booked);
    m.put("availableBeds", available);
    if (withDoctors) {
      m.put(
          "emergencyDoctorsAvailable",
          emergencyDoctors(hospital.getId()).stream()
              .filter(d -> Boolean.TRUE.equals(d.get("availableNow")))
              .count());
    }
    return m;
  }

  private Map<String, Object> toBed(EmergencyBedEntity bed) {
    EmergencyBedBookingEntity active =
        bookingRepo.findFirstByBedIdAndStatusOrderByCreatedAtDesc(bed.getId(), BOOKED).orElse(null);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", bed.getId());
    m.put("hospitalId", bed.getHospitalId());
    m.put("bedNumber", bed.getBedNumber());
    m.put("ward", bed.getWard());
    m.put("fees", bed.getFees());
    m.put("status", bed.getStatus());
    m.put("notes", bed.getNotes());
    m.put("booking", active == null ? null : toBooking(active));
    return m;
  }

  private Map<String, Object> toBooking(EmergencyBedBookingEntity booking) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", booking.getId());
    m.put("hospitalId", booking.getHospitalId());
    m.put("bedId", booking.getBedId());
    m.put("bedNumber", booking.getBedNumber());
    m.put("patientName", booking.getPatientName());
    m.put("patientPhone", booking.getPatientPhone());
    m.put("status", booking.getStatus());
    m.put("fees", booking.getFees());
    m.put("paymentId", booking.getPaymentId());
    m.put("paymentStatus", booking.getPaymentStatus());
    m.put("hasPaymentSlip", !blank(booking.getPaymentSlipPath()));
    m.put("paymentSlipName", booking.getPaymentSlipName());
    m.put("notes", booking.getNotes());
    m.put("createdAt", booking.getCreatedAt() == null ? null : booking.getCreatedAt().toString());
    return m;
  }

  private EmergencyBedBookingEntity requireBooking(String bookingId) {
    return bookingRepo
        .findById(bookingId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
  }

  private int emergencyBedCount(HospitalEntity hospital) {
    int fromJson = numberFromJson(hospital.getRegistrationJson(), "numberOfBeds");
    if (fromJson <= 0) fromJson = 12;
    int emergency = Math.max(6, (int) Math.round(fromJson * 0.3));
    return Math.min(20, emergency);
  }

  private int numberFromJson(String json, String key) {
    if (json == null || json.isBlank()) return 0;
    try {
      JsonNode node = mapper.readTree(json).get(key);
      if (node == null || node.isNull()) return 0;
      if (node.isNumber()) return node.intValue();
      String text = node.asText("");
      if (text == null || text.isBlank()) return 0;
      return Integer.parseInt(text.replaceAll("[^0-9]", ""));
    } catch (Exception ex) {
      return 0;
    }
  }

  private static boolean statusAllowsSearch(String status) {
    if (status == null || status.isBlank()) return true;
    String s = status.trim().toUpperCase(Locale.ROOT);
    return !s.contains("REJECT") && !s.equals("INACTIVE");
  }

  private static boolean containsEmergency(String value) {
    if (value == null) return false;
    String v = value.toLowerCase(Locale.ROOT);
    return v.contains("emergency")
        || v.contains("casualty")
        || v.contains("trauma")
        || v.contains("icu")
        || v.contains("critical");
  }

  private static boolean isAvailableToday(String availableDays, String today) {
    if (availableDays == null || availableDays.isBlank()) return true;
    String v = availableDays.toLowerCase(Locale.ROOT).replace(" ", "");
    if (v.contains("daily") || v.contains("all") || v.contains("24")) return true;
    DayOfWeek day = DayOfWeek.valueOf(today);
    if (v.contains(day.name().toLowerCase(Locale.ROOT))
        || v.contains(day.name().substring(0, 3).toLowerCase(Locale.ROOT))) {
      return true;
    }
    if (v.contains("mon-sat") || v.contains("monsat")) return day != DayOfWeek.SUNDAY;
    if (v.contains("mon-fri") || v.contains("monfri") || v.contains("weekday")) {
      return day.getValue() <= 5;
    }
    if (v.contains("weekend")) return day.getValue() >= 6;
    return false;
  }

  private static String formatDoctorName(String first, String last, String fallback) {
    String name = formatName(first, last);
    if (name.isBlank()) return fallback;
    return name.regionMatches(true, 0, "Dr", 0, 2) ? name : "Dr. " + name;
  }

  private static String formatName(String first, String last) {
    StringBuilder sb = new StringBuilder();
    if (first != null && !first.isBlank()) sb.append(first.trim());
    if (last != null && !last.isBlank()) {
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(last.trim());
    }
    return sb.toString();
  }

  private static String str(Map<String, Object> body, String key) {
    if (body == null || !body.containsKey(key) || body.get(key) == null) return "";
    return String.valueOf(body.get(key)).trim();
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return "";
    for (String v : values) {
      if (v != null && !v.isBlank() && !"null".equalsIgnoreCase(v.trim())) return v.trim();
    }
    return "";
  }

  private static boolean blank(String v) {
    return v == null || v.isBlank() || "null".equalsIgnoreCase(v.trim());
  }

  private static String norm(String v) {
    return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
  }

  private static String safeName(String raw) {
    String name = raw == null || raw.isBlank() ? "payment-slip.jpg" : raw;
    name = name.replace("\\", "_").replace("/", "_").replace("..", "_");
    return name.length() > 80 ? name.substring(name.length() - 80) : name;
  }

  public record Actor(
      String loginType,
      String username,
      String patientName,
      String patientPhone,
      String patientId) {}
}
