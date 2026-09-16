package com.medtrack.hrm.service;

import com.medtrack.hrm.domain.HolidayEntity;
import com.medtrack.hrm.repo.HolidayRepository;
import com.medtrack.hrm.web.AuditContext;
import java.time.LocalDate;
import java.time.format.TextStyle;
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
public class HrmHolidayService {
  private final AuditContext audit;
  private final HrmRightsService rights;
  private final HolidayRepository holidays;

  public HrmHolidayService(
      AuditContext audit, HrmRightsService rights, HolidayRepository holidays) {
    this.audit = audit;
    this.rights = rights;
    this.holidays = holidays;
  }

  public List<Map<String, Object>> list() {
    return listFrom(LocalDate.of(1900, 1, 1), 500);
  }

  public List<Map<String, Object>> upcoming() {
    return listFrom(LocalDate.now(), 50);
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> body) {
    rights.requireHolidayManager();
    if (body == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Holiday date and reason are required");
    }
    String dateRaw = str(body.get("date"));
    if (dateRaw == null || dateRaw.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Holiday date is required");
    }
    LocalDate date;
    try {
      date = LocalDate.parse(dateRaw.trim());
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Holiday date is invalid");
    }
    String reason = str(body.get("reason"));
    if (reason == null || reason.isBlank()) {
      reason = str(body.get("name"));
    }
    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason is required");
    }
    reason = reason.trim();
    Long hospitalId = audit.hospitalId();
    if (hospitalId != null && holidays.existsByHospitalIdAndHolidayDate(hospitalId, date)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A holiday already exists on this date");
    }
    HolidayEntity row = new HolidayEntity();
    row.setHospitalId(hospitalId);
    row.setHolidayDate(date);
    row.setReason(clip(reason, 400));
    row.setDescription(clip(reason, 400));
    row.setName(clip(reason, 120));
    row.touchAudit(audit.doctorId(), audit.user());
    return toMap(holidays.save(row));
  }

  @Transactional
  public void delete(String id) {
    rights.requireHolidayManager();
    HolidayEntity row =
        holidays
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Holiday not found"));
    if (audit.hospitalId() != null
        && row.getHospitalId() != null
        && !audit.hospitalId().equals(row.getHospitalId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Holiday is not in this hospital");
    }
    holidays.delete(row);
  }

  private List<Map<String, Object>> listFrom(LocalDate from, int limit) {
    Long hospitalId = audit.hospitalId();
    List<HolidayEntity> hospitalRows =
        hospitalId == null
            ? List.of()
            : holidays.findByHospitalIdAndHolidayDateGreaterThanEqualOrderByHolidayDateAsc(
                hospitalId, from);
    List<HolidayEntity> doctorRows =
        holidays.findByDoctorIdAndHolidayDateGreaterThanEqualOrderByHolidayDateAsc(
            audit.doctorId(), from);
    LinkedHashMap<String, HolidayEntity> byDate = new LinkedHashMap<>();
    for (HolidayEntity h : doctorRows) {
      if (h.getHolidayDate() != null) byDate.put(h.getHolidayDate().toString(), h);
    }
    for (HolidayEntity h : hospitalRows) {
      if (h.getHolidayDate() != null) byDate.put(h.getHolidayDate().toString(), h);
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (HolidayEntity h : byDate.values()) {
      out.add(toMap(h));
      if (out.size() >= limit) break;
    }
    return out;
  }

  static Map<String, Object> toMap(HolidayEntity h) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", h.getId());
    m.put("name", h.getName());
    m.put("date", h.getHolidayDate() == null ? null : h.getHolidayDate().toString());
    m.put("day", dayName(h.getHolidayDate()));
    String reason = h.getReason();
    if (reason == null || reason.isBlank()) reason = h.getDescription();
    if (reason == null || reason.isBlank()) reason = h.getName();
    m.put("reason", reason);
    return m;
  }

  static String dayName(LocalDate date) {
    if (date == null) return "";
    return date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
  }

  private static String str(Object o) {
    return o == null ? null : String.valueOf(o).trim();
  }

  private static String clip(String value, int max) {
    if (value == null) return "";
    String v = value.trim();
    return v.length() <= max ? v : v.substring(0, max);
  }
}
