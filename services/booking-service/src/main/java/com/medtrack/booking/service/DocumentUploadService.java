package com.medtrack.booking.service;

import com.medtrack.booking.domain.DocumentEntity;
import com.medtrack.booking.domain.HospitalEntity;
import com.medtrack.booking.repo.DocumentRepository;
import com.medtrack.booking.repo.HospitalRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentUploadService {
  private static final int MAX_FILES = 5;
  private static final String DEFAULT_DOC_TYPE = "TEST";

  private final DocumentRepository repo;
  private final HospitalRepository hospitalRepo;
  private final S3DocumentStorageService s3;
  private final Path uploadRoot;

  public DocumentUploadService(
      DocumentRepository repo,
      HospitalRepository hospitalRepo,
      S3DocumentStorageService s3,
      @Value("${medtrack.upload-dir:uploads/patient-documents}") String uploadDir) {
    this.repo = repo;
    this.hospitalRepo = hospitalRepo;
    this.s3 = s3;
    this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
  }

  @Transactional
  public DocumentEntity upload(
      Long hospitalId,
      String patientName,
      String aadhaarNumber,
      String phoneNumber,
      MultipartFile[] files,
      String creationUser) {
    if (hospitalId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hospital id is required");
    }
    String name = trimRequired(patientName, "Patient name");
    String phone = digitsOnly(trimRequired(phoneNumber, "Phone number"));
    String aadhaar = normalizeAadhaar(aadhaarNumber);
    List<MultipartFile> selected = nonEmptyFiles(files);
    if (selected.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload at least one document");
    }
    if (selected.size() > MAX_FILES) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Maximum " + MAX_FILES + " files allowed per upload");
    }

    String docId = UUID.randomUUID().toString();
    Path destDir = uploadRoot.resolve("hospital-" + hospitalId).resolve(docId);
    try {
      Files.createDirectories(destDir);
    } catch (IOException ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Could not create upload folder");
    }

    List<String> storedKeys = new ArrayList<>();
    List<String> originalNames = new ArrayList<>();
    boolean anyS3 = false;
    for (int i = 0; i < selected.size(); i++) {
      MultipartFile file = selected.get(i);
      String original = safeOriginalName(file.getOriginalFilename(), i + 1);
      String stored = (i + 1) + "_" + original;
      Path target = destDir.resolve(stored);
      byte[] bytes;
      try {
        bytes = file.getBytes();
        Files.write(target, bytes);
      } catch (IOException ex) {
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save file: " + original);
      }

      String s3Key = s3.objectKey(hospitalId, docId, stored);
      Optional<String> uploaded =
          s3.upload(s3Key, new java.io.ByteArrayInputStream(bytes), bytes.length, file.getContentType());
      if (uploaded.isPresent()) {
        storedKeys.add(uploaded.get());
        anyS3 = true;
      } else {
        // Fallback: keep local filename; download will use local path
        storedKeys.add(stored);
      }
      originalNames.add(original);
    }

    String destination =
        anyS3
            ? "s3://" + s3.getBucket() + "/" + s3.objectKey(hospitalId, docId, "").replaceAll("/$", "")
            : destDir.toString();
    String filePath =
        anyS3
            ? s3.objectKey(hospitalId, docId, "").replaceAll("/$", "")
            : destDir.toString();

    DocumentEntity row = new DocumentEntity();
    row.setId(docId);
    row.setPatientName(name);
    row.setAadhaarNumber(aadhaar);
    row.setHospitalId(hospitalId);
    row.setPhoneNumber(phone);
    row.setDocumentType(DEFAULT_DOC_TYPE);
    row.setDestinationPath(destination);
    row.setFilePath(filePath);
    row.setSourcePath(String.join(" | ", originalNames));
    row.setFileUpload1(slot(storedKeys, 0));
    row.setFileUpload2(slot(storedKeys, 1));
    row.setFileUpload3(slot(storedKeys, 2));
    row.setFileUpload4(slot(storedKeys, 3));
    row.setFileUpload5(slot(storedKeys, 4));
    row.setCreationDate(Instant.now());
    row.setCreationUser(blankToNull(creationUser));
    return repo.save(row);
  }

  @Transactional(readOnly = true)
  public List<DocumentEntity> listForHospital(Long hospitalId) {
    if (hospitalId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hospital id is required");
    }
    return repo.findByHospitalIdOrderByCreationDateDesc(hospitalId);
  }

  /** Patient portal list: WHERE phone + patient_name, with hospital name + flattened file rows. */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> listForPatient(String phone, String patientName) {
    String normalized = digitsOnly(phone);
    String name = patientName == null ? "" : patientName.trim();
    if (normalized.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phone is required");
    }
    if (name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientName is required");
    }
    List<DocumentEntity> docs = repo.findForPatientPortal(normalized, name);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (DocumentEntity d : docs) {
      String hospitalName =
          hospitalRepo
              .findById(d.getHospitalId())
              .map(HospitalEntity::getHospitalName)
              .orElse("Hospital #" + d.getHospitalId());
      String type =
          d.getDocumentType() == null || d.getDocumentType().isBlank()
              ? DEFAULT_DOC_TYPE
              : d.getDocumentType();
      List<String[]> slots = fileSlots(d);
      for (String[] slot : slots) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("slot", Integer.parseInt(slot[0]));
        row.put("patientName", d.getPatientName());
        row.put("documentType", type);
        row.put("hospitalName", hospitalName);
        row.put("hospitalId", d.getHospitalId());
        row.put("documentName", displayName(slot[1]));
        row.put("documentPath", slot[1]);
        row.put("filePath", d.getFilePath());
        row.put("destinationPath", d.getDestinationPath());
        row.put(
            "uploadDate",
            d.getCreationDate() != null ? d.getCreationDate().toString() : null);
        row.put(
            "downloadUrl",
            "/api/patient/documents/" + d.getId() + "/files/" + slot[0]);
        rows.add(row);
      }
    }
    return rows;
  }

  /**
   * Hospital / Check-document / Doctor portal download: use {@code file_path} + slot key from
   * Amazon S3, with local disk fallback.
   */
  @Transactional(readOnly = true)
  public byte[] resolveBytesForHospital(String documentId, int slot, Long hospitalId) {
    DocumentEntity doc = requireOwnedDoc(documentId, hospitalId);
    return resolveBytesFromDocument(doc, slot);
  }

  /** @deprecated Prefer {@link #resolveBytesForHospital} (S3 + local). */
  @Transactional(readOnly = true)
  public Path resolveStoredFileForHospital(String documentId, int slot, Long hospitalId) {
    DocumentEntity doc = requireOwnedDoc(documentId, hospitalId);
    return resolveLocalFile(doc, slot)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Local file missing — use S3 download via file_path"));
  }

  /**
   * Patient download: resolve path from {@code svc.documents.file_path} / {@code file_upload_*},
   * fetch from Amazon S3, fallback to local disk.
   */
  @Transactional(readOnly = true)
  public byte[] resolveBytesForPatient(String documentId, int slot, String phone) {
    String normalized = digitsOnly(phone);
    DocumentEntity doc =
        repo.findById(documentId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    if (!digitsOnly(doc.getPhoneNumber()).equals(normalized)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Document is not for this patient");
    }
    return resolveBytesFromDocument(doc, slot);
  }

  /**
   * Resolve file bytes using paths stored on the document row:
   *
   * <ol>
   *   <li>{@code file_upload_N} as full S3 key (preferred when upload succeeded)
   *   <li>{@code file_path} alone (when it stores the full object key)
   *   <li>{@code file_path}/{filename} when {@code file_path} is a folder prefix
   *   <li>{@code destination_path} ({@code s3://bucket/...}) + filename
   *   <li>Local disk under upload dir / destination_path
   * </ol>
   */
  private byte[] resolveBytesFromDocument(DocumentEntity doc, int slot) {
    if (slot < 1 || slot > MAX_FILES) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slot must be 1-5");
    }
    String slotValue = slotPath(doc, slot);
    String filePath = blankToNull(doc.getFilePath());
    String destination = blankToNull(doc.getDestinationPath());

    if ((slotValue == null || slotValue.isBlank()) && filePath == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File slot is empty");
    }

    for (String candidate : s3KeyCandidates(filePath, destination, slotValue)) {
      Optional<byte[]> fromS3 = s3.download(candidate);
      if (fromS3.isPresent()) {
        return fromS3.get();
      }
    }

    Optional<Path> local = resolveLocalFile(doc, slot);
    if (local.isPresent()) {
      try {
        return Files.readAllBytes(local.get());
      } catch (IOException ex) {
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Could not read local document");
      }
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "Could not fetch document from Amazon S3 using file_path (check bucket/keys) and no local copy found");
  }

  /** Build ordered S3 key candidates from documents.file_path + file_upload slot. */
  private static List<String> s3KeyCandidates(String filePath, String destination, String slotValue) {
    List<String> keys = new ArrayList<>();
    String fileName = fileNameOnly(slotValue);

    if (slotValue != null && !slotValue.isBlank()) {
      keys.add(slotValue.trim());
    }
    if (filePath != null) {
      keys.add(filePath);
      if (fileName != null && !filePath.endsWith(fileName)) {
        keys.add(joinPath(filePath, fileName));
      }
    }
    if (destination != null) {
      keys.add(destination);
      if (fileName != null) {
        keys.add(joinPath(destination, fileName));
      }
    }
    return keys;
  }

  private static String joinPath(String base, String name) {
    String b = base.replaceAll("[\\\\/]+$", "");
    String n = name.replaceAll("^[\\\\/]+", "");
    return b + "/" + n;
  }

  private static String fileNameOnly(String path) {
    if (path == null || path.isBlank()) return null;
    String p = path.replace('\\', '/');
    int i = p.lastIndexOf('/');
    return i >= 0 ? p.substring(i + 1) : p;
  }

  public String displayFileName(String documentId, int slot) {
    DocumentEntity doc =
        repo.findById(documentId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    String path = slotPath(doc, slot);
    return displayName(path == null ? "document" : path);
  }

  public Map<String, Object> toMap(DocumentEntity d) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", d.getId());
    m.put("patientName", d.getPatientName());
    m.put("aadhaarNumber", d.getAadhaarNumber());
    m.put("hospitalId", d.getHospitalId());
    m.put("phoneNumber", d.getPhoneNumber());
    m.put("documentType", d.getDocumentType());
    m.put("filePath", d.getFilePath());
    m.put("destinationPath", d.getDestinationPath());
    m.put("sourcePath", d.getSourcePath());
    m.put("fileUpload1", d.getFileUpload1());
    m.put("fileUpload2", d.getFileUpload2());
    m.put("fileUpload3", d.getFileUpload3());
    m.put("fileUpload4", d.getFileUpload4());
    m.put("fileUpload5", d.getFileUpload5());
    m.put("creationDate", d.getCreationDate() != null ? d.getCreationDate().toString() : null);
    m.put("creationUser", d.getCreationUser());
    m.put("updateDate", d.getUpdateDate() != null ? d.getUpdateDate().toString() : null);
    m.put("updateUser", d.getUpdateUser());
    return m;
  }

  private DocumentEntity requireOwnedDoc(String documentId, Long hospitalId) {
    if (hospitalId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hospital id is required");
    }
    DocumentEntity doc =
        repo.findById(documentId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    if (!hospitalId.equals(doc.getHospitalId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Document belongs to another hospital");
    }
    return doc;
  }

  private Optional<Path> resolveLocalFile(DocumentEntity doc, int slot) {
    String stored = slotPath(doc, slot);
    if (stored == null || stored.isBlank()) {
      return Optional.empty();
    }
    // If S3 key was stored, map to local filename (last segment)
    String fileName = stored.contains("/") ? stored.substring(stored.lastIndexOf('/') + 1) : stored;
    String dest = doc.getDestinationPath();
    Path base;
    if (dest != null && dest.startsWith("s3://")) {
      base = uploadRoot.resolve("hospital-" + doc.getHospitalId()).resolve(doc.getId());
    } else if (dest != null && !dest.isBlank()) {
      base = Path.of(dest).normalize();
    } else {
      base = uploadRoot.resolve("hospital-" + doc.getHospitalId()).resolve(doc.getId());
    }
    Path file = base.resolve(fileName).normalize();
    if (!file.startsWith(base.normalize()) || !Files.isRegularFile(file)) {
      return Optional.empty();
    }
    return Optional.of(file);
  }

  private static String slotPath(DocumentEntity doc, int slot) {
    return switch (slot) {
      case 1 -> doc.getFileUpload1();
      case 2 -> doc.getFileUpload2();
      case 3 -> doc.getFileUpload3();
      case 4 -> doc.getFileUpload4();
      case 5 -> doc.getFileUpload5();
      default -> null;
    };
  }

  private static List<String[]> fileSlots(DocumentEntity d) {
    List<String[]> out = new ArrayList<>();
    String[] vals = {
      d.getFileUpload1(),
      d.getFileUpload2(),
      d.getFileUpload3(),
      d.getFileUpload4(),
      d.getFileUpload5()
    };
    for (int i = 0; i < vals.length; i++) {
      if (vals[i] != null && !vals[i].isBlank()) {
        out.add(new String[] {String.valueOf(i + 1), vals[i]});
      }
    }
    return out;
  }

  private static String displayName(String path) {
    if (path == null || path.isBlank()) return "document";
    String name = path.replace('\\', '/');
    int slash = name.lastIndexOf('/');
    if (slash >= 0) name = name.substring(slash + 1);
    return name.replaceFirst("^\\d+_", "");
  }

  private static List<MultipartFile> nonEmptyFiles(MultipartFile[] files) {
    List<MultipartFile> out = new ArrayList<>();
    if (files == null) return out;
    for (MultipartFile f : files) {
      if (f != null && !f.isEmpty()) out.add(f);
    }
    return out;
  }

  private static String slot(List<String> names, int index) {
    return index < names.size() ? names.get(index) : null;
  }

  private static String trimRequired(String value, String label) {
    if (value == null || value.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is required");
    }
    return value.trim();
  }

  private static String digitsOnly(String raw) {
    return raw == null ? "" : raw.replaceAll("\\D", "");
  }

  private static String normalizeAadhaar(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aadhaar number is required");
    }
    String digits = raw.replaceAll("\\s+", "").trim();
    if (!digits.matches("\\d{12}")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Aadhaar number must be 12 digits");
    }
    return digits;
  }

  private static String safeOriginalName(String name, int index) {
    String base = name == null || name.isBlank() ? "file-" + index : name;
    String cleaned =
        base.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", "_").trim();
    if (cleaned.isBlank()) cleaned = "file-" + index;
    if (cleaned.length() > 180) {
      String ext = "";
      int dot = cleaned.lastIndexOf('.');
      if (dot > 0 && cleaned.length() - dot <= 12) {
        ext = cleaned.substring(dot).toLowerCase(Locale.ROOT);
        cleaned = cleaned.substring(0, Math.min(dot, 160));
      } else {
        cleaned = cleaned.substring(0, 180);
      }
      cleaned = cleaned + ext;
    }
    return cleaned;
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }
}
