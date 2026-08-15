package com.medtrack.hrm.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuditContext {
  @Value("${medtrack.hrm.default-doctor-id:seed-doctor-1}")
  private String defaultDoctorId;

  @Value("${medtrack.hrm.default-user:system}")
  private String defaultUser;

  @Value("${medtrack.hrm.default-hospital-id:10001}")
  private Long defaultHospitalId;

  public String doctorId() {
    String h = header("X-Doctor-Id");
    return h == null || h.isBlank() ? defaultDoctorId : h.trim();
  }

  public Long hospitalId() {
    String h = header("X-Hospital-Id");
    if (h == null || h.isBlank()) {
      return defaultHospitalId;
    }
    try {
      return Long.parseLong(h.trim());
    } catch (NumberFormatException ex) {
      return defaultHospitalId;
    }
  }

  public String user() {
    String h = header("X-User");
    return h == null || h.isBlank() ? defaultUser : h.trim();
  }

  private String header(String name) {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes sra) {
      return sra.getRequest().getHeader(name);
    }
    return null;
  }
}
