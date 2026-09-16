package com.medtrack.booking.service;

import com.medtrack.booking.domain.AppointmentEntity;
import com.medtrack.booking.domain.CareChatEntity;
import com.medtrack.booking.domain.HospitalEntity;
import com.medtrack.booking.repo.AppointmentRepository;
import com.medtrack.booking.repo.CareChatRepository;
import com.medtrack.booking.repo.HospitalRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CareChatService {
  private static final Logger log = LoggerFactory.getLogger(CareChatService.class);
  private static final Set<String> CHAT_EXTS = Set.of("pdf", "png", "jpg", "jpeg", "docx");
  private static final long CHAT_MAX_BYTES = 15L * 1024 * 1024;

  private final CareChatRepository chatRepo;
  private final AppointmentRepository appointmentRepo;
  private final HospitalRepository hospitalRepo;
  private final AppointmentEnrichmentService enrichment;
  private final S3DocumentStorageService s3;
  private final Path uploadRoot;

  public CareChatService(
      CareChatRepository chatRepo,
      AppointmentRepository appointmentRepo,
      HospitalRepository hospitalRepo,
      AppointmentEnrichmentService enrichment,
      S3DocumentStorageService s3,
      @Value("${medtrack.upload-dir:uploads/patient-documents}") String uploadDir) {
    this.chatRepo = chatRepo;
    this.appointmentRepo = appointmentRepo;
    this.hospitalRepo = hospitalRepo;
    this.enrichment = enrichment;
    this.s3 = s3;
    this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
  }
  @Transactional(readOnly = true)
  public Map<String, Object> thread(String appointmentId, String phone) {
    AppointmentEntity appt = requireAppointment(appointmentId);
    if (phone != null && !phone.isBlank()) {
      assertPatientOwns(appt, phone);
    }
    return threadOf(appt);
  }

  @Transactional
  public Map<String, Object> post(String appointmentId, Map<String, Object> body, String phone) {
    AppointmentEntity appt = requireAppointment(appointmentId);
    if (phone != null && !phone.isBlank()) {
      assertPatientOwns(appt, phone);
    }
    String message = text(body, "message");
    if (message == null || message.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
    }
    String senderType = firstNonBlank(text(body, "senderType"), phone != null ? "PATIENT" : "HOSPITAL")
        .toUpperCase();
    if (!List.of("HOSPITAL", "DOCTOR", "PATIENT").contains(senderType)) {
      senderType = phone != null ? "PATIENT" : "HOSPITAL";
    }
    String senderName =
        firstNonBlank(text(body, "senderName"), senderType.equals("PATIENT") ? appt.getPatientName() : senderType);

    if (appt.getHospitalId() == null || appt.getDoctorId() == null || appt.getDoctorId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment is missing hospital or doctor");
    }
    CareChatEntity row = new CareChatEntity();
    row.setId(UUID.randomUUID().toString());
    row.setAppointmentId(appt.getId());
    row.setHospitalId(appt.getHospitalId());
    row.setDoctorId(appt.getDoctorId());
    row.setPatientName(appt.getPatientName());
    row.setPatientPhone(digits(appt.getPhoneNumber()));
    row.setSenderType(senderType);
    row.setSenderName(senderName);
    row.setMessageText(message.trim());
    row.setCreatedDate(Instant.now());
    row.setCreatedUser(senderName);
    chatRepo.save(row);
    return threadOf(appt);
  }

  @Transactional
  public Map<String, Object> attach(
      String appointmentId,
      MultipartFile file,
      String senderType,
      String senderName,
      String caption,
      String phone) {
    AppointmentEntity appt = requireAppointment(appointmentId);
    if (phone != null && !phone.isBlank()) {
      assertPatientOwns(appt, phone);
    }
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
    }
    if (file.getSize() > CHAT_MAX_BYTES) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be 15 MB or smaller");
    }
    String original = safeFileName(file.getOriginalFilename());
    String ext = extensionOf(original);
    if (!CHAT_EXTS.contains(ext)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use PDF, PNG, JPG, or DOCX");
    }
    if (appt.getHospitalId() == null || appt.getDoctorId() == null || appt.getDoctorId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment is missing hospital or doctor");
    }
    String type =
        firstNonBlank(senderType, phone != null ? "PATIENT" : "HOSPITAL").toUpperCase(Locale.ROOT);
    if (!List.of("HOSPITAL", "DOCTOR", "PATIENT").contains(type)) {
      type = phone != null ? "PATIENT" : "HOSPITAL";
    }
    String name =
        firstNonBlank(senderName, type.equals("PATIENT") ? appt.getPatientName() : type);
    String id = UUID.randomUUID().toString();
    String storedName = id + "_" + original;
    String relativeKey = s3.chatObjectKey(appt.getId(), storedName);
    Path destFile = uploadRoot.resolve(relativeKey.replace('/', java.io.File.separatorChar)).normalize();
    if (!destFile.startsWith(uploadRoot)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attachment path");
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
      Files.createDirectories(destFile.getParent());
      Files.write(destFile, bytes);
    } catch (IOException ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save attachment");
    }
    if (s3.isEnabled()) {
      try {
        s3.upload(
            relativeKey,
            new java.io.ByteArrayInputStream(bytes),
            bytes.length,
            file.getContentType());
      } catch (Exception ex) {
        log.warn("Amazon S3 upload skipped for chat attachment {}; saved locally: {}", relativeKey, ex.getMessage());
      }
    }
    CareChatEntity row = new CareChatEntity();
    row.setId(id);
    row.setAppointmentId(appt.getId());
    row.setHospitalId(appt.getHospitalId());
    row.setDoctorId(appt.getDoctorId());
    row.setPatientName(appt.getPatientName());
    row.setPatientPhone(digits(appt.getPhoneNumber()));
    row.setSenderType(type);
    row.setSenderName(name);
    row.setMessageText(firstNonBlank(caption, original, "Attachment"));
    row.setDocumentUrl(relativeKey);
    row.setDocumentName(original);
    row.setCreatedDate(Instant.now());
    row.setCreatedUser(name);
    chatRepo.save(row);
    Map<String, Object> out = threadOf(appt);
    out.put("s3Uploaded", true);
    return out;
  }

  @Transactional(readOnly = true)
  public FileDownload fileBytes(String messageId, String phone, String hospitalHeader) {
    CareChatEntity row =
        chatRepo
            .findById(messageId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
    AppointmentEntity appt = requireAppointment(row.getAppointmentId());
    boolean allowed = false;
    if (phone != null && !phone.isBlank()) {
      assertPatientOwns(appt, phone);
      allowed = true;
    }
    if (!allowed && hospitalHeader != null && !hospitalHeader.isBlank()) {
      try {
        allowed = row.getHospitalId() != null && row.getHospitalId().equals(Long.parseLong(hospitalHeader.trim()));
      } catch (NumberFormatException ignored) {
        allowed = false;
      }
    }
    if (!allowed) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to open this attachment");
    }
    byte[] data = null;
    if (row.getDocumentUrl() != null && !row.getDocumentUrl().isBlank()) {
      data = s3.download(row.getDocumentUrl()).orElse(null);
      if (data == null && !row.getDocumentUrl().startsWith("patient-documents/")) {
        data = s3.download(s3.chatObjectKey(row.getAppointmentId(), Path.of(row.getDocumentUrl()).getFileName().toString())).orElse(null);
      }
      if (data == null) {
        Path local = uploadRoot.resolve(row.getDocumentUrl().replace('/', java.io.File.separatorChar)).normalize();
        if (local.startsWith(uploadRoot) && Files.exists(local)) {
          try {
            data = Files.readAllBytes(local);
          } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read attachment");
          }
        }
      }
    }
    if (data == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment file is missing");
    }
    return new FileDownload(data, row.getDocumentName(), mediaTypeOf(row.getDocumentName()));
  }

  public record FileDownload(byte[] bytes, String filename, MediaType contentType) {}

  @Transactional(readOnly = true)
  public Map<String, Object> patientThreads(String phone) {    String digits = digits(phone);
    if (digits.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phone is required");
    }
    List<AppointmentEntity> appts = appointmentRepo.findByPhoneNumberOrderByCreatedDateDesc(digits);
    Map<String, List<CareChatEntity>> chats =
        loadByAppointments(appts.stream().map(AppointmentEntity::getId).toList());
    List<Map<String, Object>> rows = new ArrayList<>();
    for (AppointmentEntity appt : appts) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("appointmentId", appt.getId());
      m.put("patientName", appt.getPatientName());
      m.put("doctorId", appt.getDoctorId());
      m.put("doctorName", enrichment.doctorName(appt.getDoctorId()));
      m.put("hospitalId", appt.getHospitalId());
      m.put(
          "hospitalName",
          appt.getHospitalId() == null
              ? ""
              : hospitalRepo.findById(appt.getHospitalId()).map(HospitalEntity::getHospitalName).orElse(""));
      m.put("tokenNumber", appt.getTokenNumber());
      m.put("status", appt.getStatus());
      m.put("appointmentDate", appt.getAppointmentDate() != null ? appt.getAppointmentDate().toString() : "");
      List<CareChatEntity> thread = chats.getOrDefault(appt.getId(), List.of());
      List<String> otherAts = otherMessageAts(thread, "PATIENT");
      m.put("chatCount", thread.size());
      m.put("unreadCount", otherAts.size());
      m.put("otherMessageAts", otherAts);
      rows.add(m);
    }
    return Map.of("count", rows.size(), "threads", rows);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> unreadHints(Collection<String> appointmentIds, String viewer) {
    String role = normalizeViewer(viewer);
    List<String> ids =
        appointmentIds == null
            ? List.of()
            : appointmentIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
    Map<String, List<String>> otherAts = new LinkedHashMap<>();
    Map<String, Long> unread = new LinkedHashMap<>();
    for (String id : ids) {
      otherAts.put(id, new ArrayList<>());
      unread.put(id, 0L);
    }
    for (Map.Entry<String, List<CareChatEntity>> e : loadByAppointments(ids).entrySet()) {
      List<String> ats = otherMessageAts(e.getValue(), role);
      otherAts.put(e.getKey(), ats);
      unread.put(e.getKey(), (long) ats.size());
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("viewer", role);
    out.put("unreadCount", unread);
    out.put("otherMessageAts", otherAts);
    return out;
  }

  public Map<String, List<CareChatEntity>> loadByAppointments(Collection<String> appointmentIds) {
    if (appointmentIds == null || appointmentIds.isEmpty()) return Map.of();
    List<String> ids =
        appointmentIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
    if (ids.isEmpty()) return Map.of();
    return chatRepo.findByAppointmentIdIn(ids).stream()
        .filter(row -> row.getAppointmentId() != null && !row.getAppointmentId().isBlank())
        .collect(Collectors.groupingBy(CareChatEntity::getAppointmentId));
  }

  public static List<String> otherMessageAts(List<CareChatEntity> rows, String viewer) {
    String role = normalizeViewer(viewer);
    List<String> ats = new ArrayList<>();
    if (rows == null) return ats;
    for (CareChatEntity row : rows) {
      if (row.getSenderType() != null && role.equalsIgnoreCase(row.getSenderType())) continue;
      if (row.getCreatedDate() != null) ats.add(row.getCreatedDate().toString());
    }
    return ats;
  }

  private static String normalizeViewer(String viewer) {
    String role = viewer == null ? "" : viewer.trim().toUpperCase(Locale.ROOT);
    if (!List.of("HOSPITAL", "DOCTOR", "PATIENT").contains(role)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "viewer must be HOSPITAL, DOCTOR, or PATIENT");
    }
    return role;
  }

  private Map<String, Object> threadOf(AppointmentEntity appt) {
    List<Map<String, Object>> messages =
        chatRepo.findByAppointmentIdOrderByCreatedDateAsc(appt.getId()).stream().map(this::toMap).toList();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("appointmentId", appt.getId());
    out.put("patientName", appt.getPatientName());
    out.put("phoneNumber", appt.getPhoneNumber());
    out.put("doctorId", appt.getDoctorId());
    out.put("hospitalId", appt.getHospitalId());
    out.put("status", appt.getStatus());
    out.put("tokenNumber", appt.getTokenNumber());
    out.put("messages", messages);
    return out;
  }

  private Map<String, Object> toMap(CareChatEntity row) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", row.getId());
    m.put("appointmentId", row.getAppointmentId());
    m.put("senderType", row.getSenderType());
    m.put("senderName", row.getSenderName());
    m.put("message", row.getMessageText());
    m.put("documentName", row.getDocumentName());
    if (row.getDocumentUrl() != null && !row.getDocumentUrl().isBlank()) {
      m.put("documentUrl", "/api/care-chats/attachments/" + row.getId());
    }
    m.put("createdAt", row.getCreatedDate() != null ? row.getCreatedDate().toString() : null);
    return m;
  }

  private AppointmentEntity requireAppointment(String id) {
    return appointmentRepo
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
  }

  private void assertPatientOwns(AppointmentEntity appt, String phone) {
    if (!digits(appt.getPhoneNumber()).equals(digits(phone))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to open this chat");
    }
  }

  private static String digits(String raw) {
    return raw == null ? "" : raw.replaceAll("\\D", "");
  }

  private static String text(Map<String, Object> body, String key) {
    Object v = body == null ? null : body.get(key);
    return v == null ? null : String.valueOf(v);
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return "";
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return "";
  }

  private static String safeFileName(String raw) {
    String name = raw == null ? "file" : Path.of(raw).getFileName().toString();
    name = name.replaceAll("[^A-Za-z0-9._-]", "_");
    if (name.isBlank()) name = "file";
    if (name.length() > 120) name = name.substring(name.length() - 120);
    return name;
  }

  private static String extensionOf(String name) {
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static MediaType mediaTypeOf(String name) {
    return switch (extensionOf(name == null ? "" : name)) {
      case "png" -> MediaType.IMAGE_PNG;
      case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
      case "pdf" -> MediaType.APPLICATION_PDF;
      default -> MediaType.APPLICATION_OCTET_STREAM;
    };
  }
}
