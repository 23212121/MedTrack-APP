package com.medtrack.hrm.service;

import com.medtrack.hrm.domain.CompanyApplicationSettingEntity;
import com.medtrack.hrm.repo.CompanyApplicationSettingRepository;
import com.medtrack.hrm.web.AuditContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HrmRightsService {
  public static final String HRM_HOME = "HRM_HOME";
  public static final String HRM_ATTENDANCE = "HRM_ATTENDANCE";
  public static final String HRM_LEAVE = "HRM_LEAVE";
  public static final String HRM_INBOX = "HRM_INBOX";
  public static final String HRM_PERFORMANCE = "HRM_PERFORMANCE";
  public static final String HRM_APPS = "HRM_APPS";
  public static final String HRM_APPROVER = "HRM_APPROVER";
  public static final String HRM_HOLIDAY = "HRM_HOLIDAY";

  public static final List<String> ALL_HRM_RIGHTS =
      List.of(
          HRM_HOME,
          HRM_ATTENDANCE,
          HRM_LEAVE,
          HRM_INBOX,
          HRM_PERFORMANCE,
          HRM_APPS,
          HRM_APPROVER,
          HRM_HOLIDAY);

  public static final List<String> DEFAULT_HRM_RIGHTS =
      List.of(HRM_HOME, HRM_ATTENDANCE, HRM_LEAVE, HRM_INBOX, HRM_PERFORMANCE, HRM_APPS);

  private final AuditContext audit;
  private final CompanyApplicationSettingRepository settings;

  public HrmRightsService(AuditContext audit, CompanyApplicationSettingRepository settings) {
    this.audit = audit;
    this.settings = settings;
  }

  @Transactional
  public Map<String, Object> currentRights() {
    ensureHospitalAdminSeed();
    Long hospitalId = audit.hospitalId();
    List<CompanyApplicationSettingEntity> rows = lookupRows(hospitalId);
    List<String> allowed;
    boolean fromSettings;
    if (rows.isEmpty()) {
      allowed = new ArrayList<>(DEFAULT_HRM_RIGHTS);
      fromSettings = false;
    } else {
      allowed =
          rows.stream()
              .filter(r -> Boolean.TRUE.equals(r.getAllowed()))
              .map(CompanyApplicationSettingEntity::getRightCode)
              .distinct()
              .toList();
      fromSettings = true;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("hospitalId", hospitalId);
    out.put("userId", currentUserId());
    out.put("fromSettings", fromSettings);
    out.put("rights", allowed);
    out.put("canApprove", allowed.contains(HRM_APPROVER));
    out.put("canManageHolidays", allowed.contains(HRM_HOLIDAY));
    out.put("catalog", ALL_HRM_RIGHTS);
    return out;
  }

  public void requireApprover() {
    if (!canApprove()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Approver right is required (company_application_setting)");
    }
  }

  public void requireHolidayManager() {
    if (!canManageHolidays()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Holiday manager right is required (company_application_setting)");
    }
  }

  @Transactional
  public boolean canManageHolidays() {
    ensureHospitalAdminSeed();
    Long hospitalId = audit.hospitalId();
    String user = currentUserId();
    String doctor = audit.doctorId();
    return settings.existsByHospitalIdAndUserIdAndRightCodeAndAllowedTrue(
            hospitalId, user, HRM_HOLIDAY)
        || (doctor != null
            && !doctor.equalsIgnoreCase(user)
            && settings.existsByHospitalIdAndUserIdAndRightCodeAndAllowedTrue(
                hospitalId, doctor, HRM_HOLIDAY));
  }

  @Transactional
  public boolean canApprove() {
    ensureHospitalAdminSeed();
    Long hospitalId = audit.hospitalId();
    String user = currentUserId();
    String doctor = audit.doctorId();
    return settings.existsByHospitalIdAndUserIdAndRightCodeAndAllowedTrue(
            hospitalId, user, HRM_APPROVER)
        || (doctor != null
            && !doctor.equalsIgnoreCase(user)
            && settings.existsByHospitalIdAndUserIdAndRightCodeAndAllowedTrue(
                hospitalId, doctor, HRM_APPROVER));
  }

  @Transactional
  public Map<String, Object> grant(Map<String, Object> body) {
    requireApprover();
    if (body == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId and rights are required");
    }
    String userId = str(body.get("userId"));
    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
    }
    userId = userId.trim();
    Object raw = body.get("rights");
    List<String> codes = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object o : list) {
        String c = o == null ? "" : String.valueOf(o).trim().toUpperCase(Locale.ROOT);
        if (ALL_HRM_RIGHTS.contains(c) && !codes.contains(c)) codes.add(c);
      }
    }
    if (userId.equalsIgnoreCase(currentUserId()) && !codes.contains(HRM_APPROVER)) {
      codes.add(HRM_APPROVER);
    }
    Long hospitalId = audit.hospitalId();
    settings.deleteByHospitalIdAndUserId(hospitalId, userId);
    for (String code : codes) {
      CompanyApplicationSettingEntity row = new CompanyApplicationSettingEntity();
      row.setHospitalId(hospitalId);
      row.setUserId(userId);
      row.setRightCode(code);
      row.setAllowed(true);
      row.touchAudit(hospitalId, audit.user());
      settings.save(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("userId", userId);
    out.put("rights", codes);
    return out;
  }

  private List<CompanyApplicationSettingEntity> lookupRows(Long hospitalId) {
    String user = currentUserId();
    List<CompanyApplicationSettingEntity> rows = settings.findByHospitalIdAndUserId(hospitalId, user);
    if (!rows.isEmpty()) return rows;
    String doctor = audit.doctorId();
    if (doctor != null && !doctor.isBlank() && !doctor.equalsIgnoreCase(user)) {
      return settings.findByHospitalIdAndUserId(hospitalId, doctor);
    }
    return rows;
  }

  private void ensureHospitalAdminSeed() {
    Long hospitalId = audit.hospitalId();
    if (hospitalId == null) return;
    seedUser(hospitalId, String.valueOf(hospitalId));
    ensureRight(hospitalId, String.valueOf(hospitalId), HRM_HOLIDAY);
    if ("HOSPITAL".equalsIgnoreCase(audit.loginType())) {
      seedUser(hospitalId, currentUserId());
      ensureRight(hospitalId, currentUserId(), HRM_HOLIDAY);
    }
  }

  private void ensureRight(Long hospitalId, String userId, String code) {
    if (hospitalId == null || userId == null || userId.isBlank() || code == null) return;
    if (settings.countByHospitalIdAndUserId(hospitalId, userId) == 0) return;
    if (settings.existsByHospitalIdAndUserIdAndRightCodeAndAllowedTrue(hospitalId, userId, code)) {
      return;
    }
    CompanyApplicationSettingEntity row = new CompanyApplicationSettingEntity();
    row.setHospitalId(hospitalId);
    row.setUserId(userId.trim());
    row.setRightCode(code);
    row.setAllowed(true);
    row.touchAudit(hospitalId, "system");
    settings.save(row);
  }

  private void seedUser(Long hospitalId, String userId) {
    if (userId == null || userId.isBlank()) return;
    if (settings.countByHospitalIdAndUserId(hospitalId, userId) > 0) return;
    for (String code : ALL_HRM_RIGHTS) {
      CompanyApplicationSettingEntity row = new CompanyApplicationSettingEntity();
      row.setHospitalId(hospitalId);
      row.setUserId(userId.trim());
      row.setRightCode(code);
      row.setAllowed(true);
      row.touchAudit(hospitalId, "system");
      settings.save(row);
    }
  }

  private String currentUserId() {
    String user = audit.user();
    if (user != null && !user.isBlank() && !"system".equalsIgnoreCase(user)) return user.trim();
    return audit.doctorId();
  }

  private static String str(Object o) {
    return o == null ? null : String.valueOf(o).trim();
  }
}
