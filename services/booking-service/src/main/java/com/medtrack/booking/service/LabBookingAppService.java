package com.medtrack.booking.service;

import com.medtrack.booking.domain.LabBookingChatEntity;
import com.medtrack.booking.domain.LabBookingEntity;
import com.medtrack.booking.domain.LabBookingNotificationEntity;
import com.medtrack.booking.domain.LabTechnicianEntity;
import com.medtrack.booking.repo.LabBookingChatRepository;
import com.medtrack.booking.repo.LabBookingNotificationRepository;
import com.medtrack.booking.repo.LabBookingRepository;
import com.medtrack.booking.repo.LabTechnicianRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LabBookingAppService {
  public static final Set<String> ACTIVE_QUEUE =
      Set.of("ACCEPTED", "VISIT_SCHEDULED", "IN_QUEUE", "REACHED_HOME");
  public static final Set<String> OPEN_FOR_CANCEL =
      Set.of("PENDING", "ACCEPTED", "VISIT_SCHEDULED", "IN_QUEUE", "REACHED_HOME");
  private static final int SLOT_MINUTES = 10;
  private static final long FREE_CANCEL_MINUTES = 10;
  private static final double DISTANCE_RATE_PER_KM = 10.0;
  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

  private final LabBookingRepository bookingRepo;
  private final LabBookingNotificationRepository notifRepo;
  private final LabBookingChatRepository chatRepo;
  private final LabTechnicianRepository labRepo;

  public LabBookingAppService(
      LabBookingRepository bookingRepo,
      LabBookingNotificationRepository notifRepo,
      LabBookingChatRepository chatRepo,
      LabTechnicianRepository labRepo) {
    this.bookingRepo = bookingRepo;
    this.notifRepo = notifRepo;
    this.chatRepo = chatRepo;
    this.labRepo = labRepo;
  }

  public record Actor(
      String loginType,
      Long hospitalId,
      String labId,
      String patientPhone,
      String patientId,
      String patientName,
      String username) {
    public boolean isLab() {
      return "LAB".equalsIgnoreCase(loginType);
    }

    public boolean isPatient() {
      return "PATIENT".equalsIgnoreCase(loginType);
    }

    public boolean isHospital() {
      return "HOSPITAL".equalsIgnoreCase(loginType);
    }

    public String label() {
      if (username != null && !username.isBlank()) return username.trim();
      if (isLab()) return labId;
      if (isPatient()) return patientName != null ? patientName : patientPhone;
      return hospitalId == null ? "system" : String.valueOf(hospitalId);
    }
  }

  @Transactional
  public Map<String, Object> create(Actor actor, Map<String, Object> body) {
    if (actor.isLab()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lab cannot create patient bookings");
    }
    Long hospitalId = toLong(body.get("hospitalId"));
    if (hospitalId == null) hospitalId = actor.hospitalId();
    if (hospitalId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hospitalId is required");
    }
    String mode = text(body, "mode");
    if (mode == null || mode.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "mode must be HOME_COLLECTION or IN_LAB");
    }
    mode = mode.trim().toUpperCase(Locale.ROOT);
    if (!mode.equals("HOME_COLLECTION") && !mode.equals("IN_LAB")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "mode must be HOME_COLLECTION or IN_LAB");
    }
    String patientName = firstNonBlank(text(body, "patientName"), actor.patientName());
    String patientPhone = digits(firstNonBlank(text(body, "patientPhone"), actor.patientPhone()));
    if (patientName == null || patientName.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientName is required");
    }
    if (patientPhone.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientPhone is required");
    }
    String address = text(body, "collectionAddress");
    if (address == null || address.isBlank()) address = text(body, "address");
    if (mode.equals("HOME_COLLECTION") && (address == null || address.isBlank())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "collectionAddress is required for home collection");
    }

    LabBookingEntity b = new LabBookingEntity();
    b.setId("labbook-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    b.setBookingNumber(nextBookingNumber(hospitalId));
    b.setHospitalId(hospitalId);
    b.setPatientId(firstNonBlank(text(body, "patientId"), actor.patientId()));
    b.setPatientName(patientName.trim());
    b.setPatientPhone(patientPhone);
    b.setMode(mode);
    b.setCollectionAddress(address == null || address.isBlank() ? null : address.trim());
    double[] gps = patientGps(body, b.getCollectionAddress());
    if (gps != null) {
      b.setPatientLatitude(gps[0]);
      b.setPatientLongitude(gps[1]);
    }
    b.setNotes(text(body, "notes"));
    Instant preferred = parseInstant(firstNonBlank(text(body, "preferredAt"), text(body, "preferredTime")));
    b.setPreferredAt(preferred);
    b.setStatus("PENDING");
    b.setCreatedAt(Instant.now());
    b.setCreatedBy(actor.label());
    bookingRepo.save(b);

    notifyLabs(
        b,
        "New lab booking",
        "Lab booking "
            + b.getBookingNumber()
            + " ("
            + modeLabel(mode)
            + ") from "
            + b.getPatientName()
            + ". Accept to schedule.");
    return Map.of("message", "Lab booking created", "booking", toMap(b, actor));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> list(Actor actor, String status) {
    List<LabBookingEntity> rows;
    if (actor.isLab()) {
      LabTechnicianEntity lab = requireLab(actor);
      List<LabBookingEntity> pending =
          bookingRepo.findByHospitalIdAndStatusOrderByCreatedAtDesc(lab.getHospitalId(), "PENDING");
      List<LabBookingEntity> mine =
          bookingRepo.findByAssignedLabIdOrderByCreatedAtDesc(lab.getId());
      Map<String, LabBookingEntity> merged = new LinkedHashMap<>();
      for (LabBookingEntity row : pending) merged.put(row.getId(), row);
      for (LabBookingEntity row : mine) merged.put(row.getId(), row);
      rows = new ArrayList<>(merged.values());
      rows.sort(
          Comparator.comparing(
              LabBookingEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
    } else if (actor.isPatient()) {
      String phone = digits(actor.patientPhone());
      if (phone.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient phone is required");
      }
      rows = bookingRepo.findByPatientPhoneOrderByCreatedAtDesc(phone);
    } else if (actor.isHospital() && actor.hospitalId() != null) {
      rows = bookingRepo.findByHospitalIdOrderByCreatedAtDesc(actor.hospitalId());
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
    }
    if (status != null && !status.isBlank()) {
      String st = status.trim().toUpperCase(Locale.ROOT);
      rows = rows.stream().filter(r -> st.equalsIgnoreCase(r.getStatus())).toList();
    }
    List<Map<String, Object>> out = rows.stream().map(r -> toMap(r, actor)).toList();
    return Map.of("count", out.size(), "bookings", out);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(Actor actor, String id) {
    LabBookingEntity b = require(id);
    assertCanView(actor, b);
    return Map.of("booking", toMap(b, actor));
  }

  @Transactional
  public Map<String, Object> accept(Actor actor, Map<String, Object> body, String id) {
    requireLabActor(actor);
    LabTechnicianEntity lab = requireActiveLab(actor);
    LabBookingEntity b = require(id);
    if (b.getHospitalId() != null
        && lab.getHospitalId() != null
        && !b.getHospitalId().equals(lab.getHospitalId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Booking belongs to another hospital");
    }
    if (!"PENDING".equalsIgnoreCase(b.getStatus()) || b.getAssignedLabId() != null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This booking was already accepted by another lab technician");
    }

    Instant now = Instant.now();
    b.setAssignedLabId(lab.getId());
    b.setAssignedLabName(lab.getLabName());
    b.setConfirmedAt(now);
    b.setUpdatedAt(now);
    b.setUpdatedBy(actor.label());

    if ("HOME_COLLECTION".equalsIgnoreCase(b.getMode())) {
      Instant visitTime =
          parseInstant(firstNonBlank(text(body, "visitTime"), text(body, "visitAt")));
      if (visitTime == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "visitTime is required when accepting a home collection");
      }
      b.setVisitTime(visitTime);
      b.setExpectedVisitAt(visitTime);
      b.setQueueDate(LocalDate.ofInstant(visitTime, ZONE));
      b.setStatus("VISIT_SCHEDULED");
      applyTripCharge(b, body);
    } else {
      assignInLabToken(b, lab);
      b.setStatus("IN_QUEUE");
      if (b.getVisitTime() == null) {
        b.setVisitTime(b.getExpectedVisitAt());
      }
    }
    bookingRepo.save(b);

    String timeNote =
        b.getExpectedVisitAt() == null
            ? ""
            : " Scheduled time: " + b.getExpectedVisitAt() + ".";
    if (b.getTokenNumber() != null) {
      timeNote += " Token #" + b.getTokenNumber() + ".";
    }
    notifyPatient(
        b,
        "Lab booking accepted",
        "Your lab booking "
            + b.getBookingNumber()
            + " was accepted by "
            + lab.getLabName()
            + "."
            + timeNote);
    return Map.of("message", "Booking accepted", "booking", toMap(b, actor));
  }

  @Transactional
  public Map<String, Object> reachedHome(Actor actor, String id) {
    requireLabActor(actor);
    LabTechnicianEntity lab = requireActiveLab(actor);
    LabBookingEntity b = requireAssigned(actor, lab, id);
    if (!"HOME_COLLECTION".equalsIgnoreCase(b.getMode())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only home collection can mark reached");
    }
    if (!Set.of("VISIT_SCHEDULED", "ACCEPTED").contains(b.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot mark reached in status " + b.getStatus());
    }
    b.setTechReachedAt(Instant.now());
    b.setStatus("REACHED_HOME");
    b.setUpdatedAt(Instant.now());
    b.setUpdatedBy(actor.label());
    bookingRepo.save(b);
    notifyPatient(
        b,
        "Technician reached home",
        "The lab technician for booking "
            + b.getBookingNumber()
            + " has reached your home. Cancelling now may include a distance charge.");
    return Map.of("message", "Marked reached home", "booking", toMap(b, actor));
  }

  @Transactional
  public Map<String, Object> complete(Actor actor, String id) {
    requireLabActor(actor);
    LabTechnicianEntity lab = requireActiveLab(actor);
    LabBookingEntity b = requireAssigned(actor, lab, id);
    if (Set.of("COMPLETED", "CANCELED").contains(b.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking already closed");
    }
    b.setStatus("COMPLETED");
    b.setCompletedAt(Instant.now());
    b.setCompletedBy(actor.label());
    b.setUpdatedAt(Instant.now());
    b.setUpdatedBy(actor.label());
    bookingRepo.save(b);
    notifyPatient(
        b,
        "Lab booking completed",
        "Your lab booking " + b.getBookingNumber() + " is completed.");
    return Map.of("message", "Booking completed", "booking", toMap(b, actor));
  }

  @Transactional
  public Map<String, Object> cancel(Actor actor, Map<String, Object> body, String id) {
    LabBookingEntity b = require(id);
    assertCanView(actor, b);
    if (!OPEN_FOR_CANCEL.contains(b.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Booking cannot be canceled in status " + b.getStatus());
    }
    if (actor.isLab()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Lab technicians cannot cancel; ask the patient or mark complete");
    }
    if (actor.isPatient()) {
      assertPatientOwns(actor, b);
    }

    double charge = 0;
    Double distanceKm = null;
    if (b.getTechReachedAt() != null) {
      distanceKm = toDouble(body.get("distanceKm"), body.get("distance"));
      if (distanceKm == null || distanceKm < 0) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "distanceKm is required when the technician has already reached home");
      }
      charge = Math.round(distanceKm * DISTANCE_RATE_PER_KM * 100.0) / 100.0;
    } else if (b.getConfirmedAt() != null) {
      long mins = Duration.between(b.getConfirmedAt(), Instant.now()).toMinutes();
      // Free within 10 minutes; after that still free (no distance) per plan scope
      if (mins <= FREE_CANCEL_MINUTES) {
        charge = 0;
      }
    }

    String reason = firstNonBlank(text(body, "reason"), "Canceled by patient");
    b.setStatus("CANCELED");
    b.setCancelReason(reason);
    b.setCancelCharge(charge);
    b.setDistanceKm(distanceKm);
    b.setCancelledBy(actor.label());
    b.setCancelledAt(Instant.now());
    b.setUpdatedAt(Instant.now());
    b.setUpdatedBy(actor.label());
    bookingRepo.save(b);

    String chargeNote =
        charge > 0 ? " Distance cancel charge: ₹" + charge + "." : " No cancel charge.";
    notifyPatient(b, "Lab booking canceled", "Booking " + b.getBookingNumber() + " canceled." + chargeNote);
    if (b.getAssignedLabId() != null) {
      notifyLab(
          b,
          b.getAssignedLabId(),
          "Lab booking canceled",
          "Booking " + b.getBookingNumber() + " was canceled." + chargeNote);
    }
    return Map.of("message", "Booking canceled", "booking", toMap(b, actor));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> listChats(Actor actor, String id) {
    LabBookingEntity b = require(id);
    assertCanView(actor, b);
    List<Map<String, Object>> messages =
        chatRepo.findByBookingIdOrderByCreatedAtAsc(id).stream().map(this::chatMap).toList();
    return Map.of("bookingId", id, "count", messages.size(), "messages", messages);
  }

  @Transactional
  public Map<String, Object> postChat(Actor actor, String id, Map<String, Object> body) {
    LabBookingEntity b = require(id);
    assertCanView(actor, b);
    if ("PENDING".equalsIgnoreCase(b.getStatus())
        || "CANCELED".equalsIgnoreCase(b.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Chat opens after the lab accepts and while the booking is open");
    }
    String text = text(body, "message");
    if (text == null || text.isBlank()) text = text(body, "messageText");
    if (text == null || text.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
    }
    String senderType = actor.isLab() ? "LAB" : actor.isPatient() ? "PATIENT" : "HOSPITAL";
    LabBookingChatEntity chat = new LabBookingChatEntity();
    chat.setId("labchat-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    chat.setBookingId(id);
    chat.setHospitalId(b.getHospitalId());
    chat.setSenderType(senderType);
    chat.setSenderName(firstNonBlank(text(body, "senderName"), actor.label()));
    chat.setMessageText(text.trim());
    chat.setCreatedAt(Instant.now());
    chat.setCreatedBy(actor.label());
    chatRepo.save(chat);

    if (actor.isPatient() && b.getAssignedLabId() != null) {
      notifyLab(b, b.getAssignedLabId(), "New chat message", b.getPatientName() + ": " + truncate(text, 120));
    } else if (actor.isLab()) {
      notifyPatient(b, "New chat message", (b.getAssignedLabName() == null ? "Lab" : b.getAssignedLabName()) + ": " + truncate(text, 120));
    }
    return Map.of("message", "Sent", "chat", chatMap(chat), "bookingId", id);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> notifications(Actor actor) {
    List<LabBookingNotificationEntity> rows;
    if (actor.isLab()) {
      LabTechnicianEntity lab = requireLab(actor);
      rows = notifRepo.findByLabIdOrderByCreatedAtDesc(lab.getId());
    } else if (actor.isPatient()) {
      rows = notifRepo.findByPatientPhoneOrderByCreatedAtDesc(digits(actor.patientPhone()));
    } else if (actor.isHospital() && actor.hospitalId() != null) {
      rows = notifRepo.findByHospitalIdAndAudienceOrderByCreatedAtDesc(actor.hospitalId(), "LAB");
    } else {
      rows = List.of();
    }
    List<Map<String, Object>> out = rows.stream().limit(100).map(this::notifMap).toList();
    return Map.of("count", out.size(), "notifications", out);
  }

  @Transactional
  public Map<String, Object> markRead(Actor actor, String notifId) {
    LabBookingNotificationEntity n =
        notifRepo
            .findById(notifId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    n.setReadFlag(true);
    notifRepo.save(n);
    return Map.of("notification", notifMap(n));
  }

  private void assignInLabToken(LabBookingEntity b, LabTechnicianEntity lab) {
    LocalDate queueDate =
        b.getPreferredAt() != null
            ? LocalDate.ofInstant(b.getPreferredAt(), ZONE)
            : LocalDate.now(ZONE);
    b.setQueueDate(queueDate);

    List<LabBookingEntity> active =
        bookingRepo.findByHospitalIdAndQueueDateAndModeAndStatusInOrderByCreatedAtAsc(
            b.getHospitalId(), queueDate, "IN_LAB", ACTIVE_QUEUE);
    // Prefer same lab when already assigned rows exist; include unassigned hospital queue too for pending accept days
    List<LabBookingEntity> ahead =
        active.stream()
            .filter(
                row ->
                    row.getAssignedLabId() == null
                        || row.getAssignedLabId().equals(lab.getId())
                        || !row.getId().equals(b.getId()))
            .filter(row -> !row.getId().equals(b.getId()))
            .filter(
                row ->
                    row.getAssignedLabId() == null || lab.getId().equals(row.getAssignedLabId()))
            .toList();

    int token = ahead.size() + 1;
    Instant anchor =
        ahead.stream()
            .map(LabBookingEntity::getExpectedVisitAt)
            .filter(t -> t != null)
            .min(Instant::compareTo)
            .orElse(null);
    if (anchor == null) {
      if (b.getPreferredAt() != null) {
        anchor = b.getPreferredAt();
      } else {
        anchor = LocalDateTime.of(queueDate, LocalTime.of(9, 0)).atZone(ZONE).toInstant();
      }
    }
    Instant expected = anchor.plus(Duration.ofMinutes((long) (token - 1) * SLOT_MINUTES));
    b.setTokenNumber(token);
    b.setExpectedVisitAt(expected);
  }

  private String nextBookingNumber(Long hospitalId) {
    String prefix = "LB-" + hospitalId + "-";
    long seq = bookingRepo.findByHospitalIdOrderByCreatedAtDesc(hospitalId).size() + 1;
    String candidate = prefix + seq;
    while (bookingRepo.findByBookingNumberIgnoreCase(candidate).isPresent()) {
      seq++;
      candidate = prefix + seq;
    }
    return candidate;
  }

  private void notifyLabs(LabBookingEntity b, String title, String message) {
    List<LabTechnicianEntity> labs =
        labRepo.findByHospitalIdAndStatusOrderByLabNameAsc(b.getHospitalId(), "ACTIVE");
    for (LabTechnicianEntity lab : labs) {
      notifyLab(b, lab.getId(), title, message);
    }
  }

  private void notifyLab(LabBookingEntity b, String labId, String title, String message) {
    LabBookingNotificationEntity n = new LabBookingNotificationEntity();
    n.setId("labnotif-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    n.setHospitalId(b.getHospitalId());
    n.setLabId(labId);
    n.setAudience("LAB");
    n.setBookingId(b.getId());
    n.setTitle(title);
    n.setMessage(message);
    n.setReadFlag(false);
    n.setCreatedAt(Instant.now());
    notifRepo.save(n);
  }

  private void notifyPatient(LabBookingEntity b, String title, String message) {
    LabBookingNotificationEntity n = new LabBookingNotificationEntity();
    n.setId("labnotif-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    n.setHospitalId(b.getHospitalId());
    n.setPatientPhone(b.getPatientPhone());
    n.setAudience("PATIENT");
    n.setBookingId(b.getId());
    n.setTitle(title);
    n.setMessage(message);
    n.setReadFlag(false);
    n.setCreatedAt(Instant.now());
    notifRepo.save(n);
  }

  private LabBookingEntity require(String id) {
    return bookingRepo
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab booking not found"));
  }

  private LabBookingEntity requireAssigned(Actor actor, LabTechnicianEntity lab, String id) {
    LabBookingEntity b = require(id);
    if (b.getAssignedLabId() == null || !lab.getId().equals(b.getAssignedLabId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Booking is not assigned to this lab");
    }
    return b;
  }

  private LabTechnicianEntity requireLab(Actor actor) {
    if (actor.labId() == null || actor.labId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Lab session is required");
    }
    return labRepo
        .findById(actor.labId().trim())
        .or(() -> labRepo.findByLabCodeIgnoreCase(actor.labId().trim()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Lab not found"));
  }

  private LabTechnicianEntity requireActiveLab(Actor actor) {
    LabTechnicianEntity lab = requireLab(actor);
    if (!"ACTIVE".equalsIgnoreCase(lab.getStatus())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lab is inactive");
    }
    return lab;
  }

  private void requireLabActor(Actor actor) {
    if (!actor.isLab()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lab technician login required");
    }
  }

  private void assertCanView(Actor actor, LabBookingEntity b) {
    if (actor.isLab()) {
      LabTechnicianEntity lab = requireLab(actor);
      if (b.getAssignedLabId() != null && !lab.getId().equals(b.getAssignedLabId())) {
        if (!"PENDING".equalsIgnoreCase(b.getStatus())
            || (lab.getHospitalId() != null && !lab.getHospitalId().equals(b.getHospitalId()))) {
          throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your booking");
        }
      } else if (b.getAssignedLabId() == null
          && lab.getHospitalId() != null
          && !lab.getHospitalId().equals(b.getHospitalId())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Different hospital");
      }
      return;
    }
    if (actor.isPatient()) {
      assertPatientOwns(actor, b);
      return;
    }
    if (actor.isHospital()
        && actor.hospitalId() != null
        && actor.hospitalId().equals(b.getHospitalId())) {
      return;
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
  }

  private void assertPatientOwns(Actor actor, LabBookingEntity b) {
    String phone = digits(actor.patientPhone());
    if (phone.isBlank() || !phone.equals(digits(b.getPatientPhone()))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your booking");
    }
  }

  private Map<String, Object> toMap(LabBookingEntity b, Actor actor) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", b.getId());
    m.put("bookingNumber", b.getBookingNumber());
    m.put("hospitalId", b.getHospitalId());
    m.put("patientId", b.getPatientId());
    m.put("patientName", b.getPatientName());
    m.put("patientPhone", b.getPatientPhone());
    m.put("mode", b.getMode());
    m.put("modeLabel", modeLabel(b.getMode()));
    m.put("collectionAddress", b.getCollectionAddress());
    m.put("patientLatitude", b.getPatientLatitude());
    m.put("patientLongitude", b.getPatientLongitude());
    m.put("tripDistanceKm", b.getTripDistanceKm());
    m.put("tripCharge", b.getTripCharge());
    m.put("distanceRatePerKm", DISTANCE_RATE_PER_KM);
    m.put("notes", b.getNotes());
    m.put("preferredAt", iso(b.getPreferredAt()));
    m.put("status", b.getStatus());
    m.put("assignedLabId", b.getAssignedLabId());
    m.put("assignedLabName", b.getAssignedLabName());
    m.put("confirmedAt", iso(b.getConfirmedAt()));
    m.put("visitTime", iso(b.getVisitTime()));
    m.put("tokenNumber", b.getTokenNumber());
    m.put("expectedVisitAt", iso(b.getExpectedVisitAt()));
    m.put("queueDate", b.getQueueDate() == null ? null : b.getQueueDate().toString());
    m.put("techReachedAt", iso(b.getTechReachedAt()));
    m.put("cancelReason", b.getCancelReason());
    m.put("cancelCharge", b.getCancelCharge());
    m.put("distanceKm", b.getDistanceKm());
    m.put("cancelledAt", iso(b.getCancelledAt()));
    m.put("completedAt", iso(b.getCompletedAt()));
    m.put("createdAt", iso(b.getCreatedAt()));
    m.put("freeCancelUntil", freeCancelUntil(b));
    m.put("freeCancelRemainingSeconds", freeCancelRemainingSeconds(b));
    m.put("canChat", b.getAssignedLabId() != null && !Set.of("PENDING", "CANCELED").contains(b.getStatus()));
    m.put("slotMinutes", SLOT_MINUTES);
    if (actor != null && actor.isPatient()) {
      m.put("canCancel", OPEN_FOR_CANCEL.contains(b.getStatus()));
    }
    return m;
  }

  private String freeCancelUntil(LabBookingEntity b) {
    if (b.getConfirmedAt() == null || b.getTechReachedAt() != null) return null;
    return iso(b.getConfirmedAt().plus(Duration.ofMinutes(FREE_CANCEL_MINUTES)));
  }

  private long freeCancelRemainingSeconds(LabBookingEntity b) {
    if (b.getConfirmedAt() == null || b.getTechReachedAt() != null) return 0;
    long left =
        Duration.between(Instant.now(), b.getConfirmedAt().plus(Duration.ofMinutes(FREE_CANCEL_MINUTES)))
            .getSeconds();
    return Math.max(0, left);
  }

  private Map<String, Object> chatMap(LabBookingChatEntity c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId());
    m.put("bookingId", c.getBookingId());
    m.put("senderType", c.getSenderType());
    m.put("senderName", c.getSenderName());
    m.put("message", c.getMessageText());
    m.put("createdAt", iso(c.getCreatedAt()));
    return m;
  }

  private Map<String, Object> notifMap(LabBookingNotificationEntity n) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", n.getId());
    m.put("bookingId", n.getBookingId());
    m.put("audience", n.getAudience());
    m.put("title", n.getTitle());
    m.put("message", n.getMessage());
    m.put("read", n.isReadFlag());
    m.put("createdAt", iso(n.getCreatedAt()));
    return m;
  }

  private void applyTripCharge(LabBookingEntity b, Map<String, Object> body) {
    Double km = toDouble(body.get("distanceKm"), body.get("tripDistanceKm"));
    if (km == null || km < 0) return;
    double rounded = Math.round(km * 100.0) / 100.0;
    b.setTripDistanceKm(rounded);
    b.setTripCharge(Math.round(rounded * DISTANCE_RATE_PER_KM * 100.0) / 100.0);
  }

  private static double[] patientGps(Map<String, Object> body, String address) {
    Double lat = toDouble(body.get("latitude"), body.get("patientLatitude"));
    Double lng = toDouble(body.get("longitude"), body.get("patientLongitude"));
    if (lat != null && lng != null) return new double[] {lat, lng};
    if (address == null) return null;
    int idx = address.toUpperCase(Locale.ROOT).lastIndexOf("GPS:");
    if (idx < 0) return null;
    String rest = address.substring(idx + 4).trim();
    String[] parts = rest.split(",");
    if (parts.length < 2) return null;
    try {
      return new double[] {Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())};
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static String modeLabel(String mode) {
    if ("HOME_COLLECTION".equalsIgnoreCase(mode)) return "Home sample collection";
    if ("IN_LAB".equalsIgnoreCase(mode)) return "Visit lab";
    return mode;
  }

  private static String iso(Instant instant) {
    return instant == null ? null : instant.toString();
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    String t = s.trim();
    return t.length() <= max ? t : t.substring(0, max) + "…";
  }

  private static Instant parseInstant(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String v = raw.trim();
    try {
      return Instant.parse(v);
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      if (v.length() == 16) {
        return LocalDateTime.parse(v).atZone(ZONE).toInstant();
      }
      return LocalDateTime.parse(v).atZone(ZONE).toInstant();
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      return LocalDate.parse(v).atTime(9, 0).atZone(ZONE).toInstant();
    } catch (DateTimeParseException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid datetime: " + raw);
    }
  }

  private static String text(Map<String, Object> body, String key) {
    if (body == null || !body.containsKey(key) || body.get(key) == null) return null;
    String s = String.valueOf(body.get(key)).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }

  private static String digits(String raw) {
    if (raw == null) return "";
    return raw.replaceAll("\\D+", "");
  }

  private static Long toLong(Object raw) {
    if (raw == null) return null;
    if (raw instanceof Number n) return n.longValue();
    String s = String.valueOf(raw).trim();
    if (s.isEmpty()) return null;
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Double toDouble(Object... raws) {
    for (Object raw : raws) {
      if (raw == null) continue;
      if (raw instanceof Number n) return n.doubleValue();
      try {
        return Double.parseDouble(String.valueOf(raw).trim());
      } catch (NumberFormatException ignored) {
        // try next
      }
    }
    return null;
  }
}
