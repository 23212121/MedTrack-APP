package com.medtrack.hrm.service;

import com.medtrack.hrm.domain.EmployeeEntity;
import com.medtrack.hrm.domain.HrmInboxEntity;
import com.medtrack.hrm.repo.HrmInboxRepository;
import com.medtrack.hrm.web.AuditContext;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HrmInboxService {
  private final AuditContext audit;
  private final HrmInboxRepository inbox;
  private final HrmAppService hrm;

  public HrmInboxService(
      AuditContext audit, HrmInboxRepository inbox, @Lazy HrmAppService hrm) {
    this.audit = audit;
    this.inbox = inbox;
    this.hrm = hrm;
  }

  @Transactional
  public Map<String, Object> list(String view, String category) {
    ensureDemoItems();
    Long hospitalId = audit.hospitalId();
    String doctorId = audit.doctorId();
    boolean archived = "archive".equalsIgnoreCase(view);

    List<HrmInboxEntity> items =
        inbox.findByHospitalIdAndDoctorIdAndArchivedOrderByRequestedAtDesc(
            hospitalId, doctorId, archived);

    if ("notifications".equalsIgnoreCase(view)) {
      // Notifications = non-pending acknowledged items still not archived
      items =
          items.stream()
              .filter(i -> !"PENDING".equalsIgnoreCase(i.getStatus()))
              .toList();
    } else if ("action".equalsIgnoreCase(view) || view == null || view.isBlank()) {
      items =
          items.stream().filter(i -> "PENDING".equalsIgnoreCase(i.getStatus())).toList();
    }

    if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
      String cat = category.trim().toUpperCase(Locale.ROOT);
      items =
          items.stream()
              .filter(i -> cat.equalsIgnoreCase(i.getCategory()) || "DOCUMENTS".equals(cat))
              .toList();
    }

    Map<String, Long> categoryCounts = new LinkedHashMap<>();
    for (HrmInboxEntity i :
        inbox.findByHospitalIdAndDoctorIdAndArchivedOrderByRequestedAtDesc(
            hospitalId, doctorId, false)) {
      if (!"PENDING".equalsIgnoreCase(i.getStatus())) continue;
      categoryCounts.merge(bucket(i.getCategory()), 1L, Long::sum);
    }

    long pendingCount =
        inbox.countByHospitalIdAndDoctorIdAndStatusAndArchived(
            hospitalId, doctorId, "PENDING", false);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("hospitalId", hospitalId);
    out.put("doctorId", doctorId);
    out.put("view", view == null ? "action" : view);
    out.put("pendingCount", pendingCount);
    out.put("categoryCounts", categoryCounts);
    out.put("items", items);
    return out;
  }

  public HrmInboxEntity get(String id) {
    return inbox
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inbox item not found"));
  }

  @Transactional
  public HrmInboxEntity releaseDocument(Map<String, Object> body) {
    EmployeeEntity emp = hrm.ensureSelfEmployeeWithBalances();
    String category = normalizeCategory(str(body.get("category")));
    String documentName = str(body.get("documentName"));
    if (documentName == null || documentName.isBlank()) {
      documentName = str(body.get("title"));
    }
    if (documentName == null || documentName.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentName is required");
    }
    String title = str(body.get("title"));
    if (title == null || title.isBlank()) title = documentName;

    HrmInboxEntity item = new HrmInboxEntity();
    item.setEmployeeId(emp.getEmployeeId());
    item.setEmployeeName(emp.getFullName());
    item.setCategory(category);
    item.setDocumentName(documentName);
    item.setTitle(title);
    item.setStatus("PENDING");
    item.setActionRequired(
        Optional.ofNullable(str(body.get("actionRequired"))).orElse("Acknowledgement Required"));
    item.setMessageBody(buildMessage(emp.getFullName(), documentName, item.getActionRequired()));
    item.setFileUrl(str(body.get("fileUrl")));
    item.setRequestedBy(
        Optional.ofNullable(str(body.get("requestedBy"))).orElse(audit.user()));
    item.setRequestedAt(Instant.now());
    item.setArchived(false);
    item.touchAudit(audit.hospitalId(), audit.doctorId(), audit.user());
    return inbox.save(item);
  }

  @Transactional
  public HrmInboxEntity acknowledge(String id) {
    HrmInboxEntity item = get(id);
    item.setStatus("ACKNOWLEDGED");
    item.setAcknowledgedAt(Instant.now());
    item.setArchived(true);
    item.touchAudit(audit.hospitalId(), audit.doctorId(), audit.user());
    return inbox.save(item);
  }

  @Transactional
  public HrmInboxEntity markDownloaded(String id) {
    HrmInboxEntity item = get(id);
    item.setStatus("DOWNLOADED");
    item.setAcknowledgedAt(Instant.now());
    item.touchAudit(audit.hospitalId(), audit.doctorId(), audit.user());
    return inbox.save(item);
  }

  @Transactional
  public HrmInboxEntity archive(String id) {
    HrmInboxEntity item = get(id);
    item.setArchived(true);
    if ("PENDING".equalsIgnoreCase(item.getStatus())) {
      item.setStatus("ARCHIVED");
    }
    item.touchAudit(audit.hospitalId(), audit.doctorId(), audit.user());
    return inbox.save(item);
  }

  @Transactional
  public List<HrmInboxEntity> ensureDemoItems() {
    EmployeeEntity emp = hrm.ensureSelfEmployeeWithBalances();
    Long hospitalId = audit.hospitalId();
    String doctorId = audit.doctorId();
    List<String[]> seeds =
        List.of(
            new String[] {
              "SALARY_INCREMENT",
              "Salary Increment Letter_BSS New for Permanent Employees",
              "Salary Increment Letter"
            },
            new String[] {
              "PERFORMANCE",
              "Annual Performance Review 2026",
              "Performance Review Letter"
            },
            new String[] {
              "LETTER_RELEASE",
              "Experience / Relieving Letter Release",
              "Letter Release"
            });

    List<HrmInboxEntity> created = new ArrayList<>();
    for (String[] row : seeds) {
      if (inbox.existsByHospitalIdAndDoctorIdAndCategoryAndDocumentName(
          hospitalId, doctorId, row[0], row[1])) {
        continue;
      }
      HrmInboxEntity item = new HrmInboxEntity();
      item.setEmployeeId(emp.getEmployeeId());
      item.setEmployeeName(emp.getFullName());
      item.setCategory(row[0]);
      item.setDocumentName(row[1]);
      item.setTitle(row[1]);
      item.setStatus("PENDING");
      item.setActionRequired("Acknowledgement Required");
      item.setMessageBody(buildMessage(emp.getFullName(), row[1], "Acknowledgement"));
      item.setFileUrl("/api/hrm/inbox/files/" + row[0].toLowerCase(Locale.ROOT) + ".pdf");
      item.setRequestedBy("HR Admin");
      item.setRequestedAt(Instant.now().minusSeconds(86400L * 90));
      item.setArchived(false);
      item.touchAudit(hospitalId, doctorId, audit.user());
      created.add(inbox.save(item));
    }
    return created;
  }

  private static String bucket(String category) {
    if (category == null) return "Documents";
    return switch (category.toUpperCase(Locale.ROOT)) {
      case "PERFORMANCE" -> "Performance";
      case "LETTER_RELEASE" -> "Letter release";
      case "SALARY_INCREMENT" -> "Salary increment";
      default -> "Documents";
    };
  }

  private static String normalizeCategory(String raw) {
    if (raw == null || raw.isBlank()) return "DOCUMENTS";
    String c = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    return switch (c) {
      case "PERFORMANCE", "PERF" -> "PERFORMANCE";
      case "LETTER_RELEASE", "LETTER", "LATTER", "LATTER_RELEASE" -> "LETTER_RELEASE";
      case "SALARY_INCREMENT", "SALARY", "INCREMENT" -> "SALARY_INCREMENT";
      default -> "DOCUMENTS";
    };
  }

  private static String buildMessage(String name, String doc, String action) {
    return "Hello "
        + name
        + ",\n\n"
        + name
        + "'s "
        + doc
        + " requires your "
        + action
        + ".";
  }

  private static String str(Object o) {
    return o == null ? null : String.valueOf(o).trim();
  }
}
