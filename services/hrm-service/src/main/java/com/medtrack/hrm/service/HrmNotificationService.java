package com.medtrack.hrm.service;

import com.medtrack.common.dto.NotifyRequest;
import com.medtrack.hrm.domain.EmployeeEntity;
import com.medtrack.hrm.repo.EmployeeRepository;
import com.medtrack.hrm.web.AuditContext;
import com.medtrack.notify.service.NotificationAppService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HrmNotificationService {
  private static final Logger log = LoggerFactory.getLogger(HrmNotificationService.class);

  private final NotificationAppService notifications;
  private final EmployeeRepository employees;
  private final AuditContext audit;

  public HrmNotificationService(
      NotificationAppService notifications, EmployeeRepository employees, AuditContext audit) {
    this.notifications = notifications;
    this.employees = employees;
    this.audit = audit;
  }

  public void notify(String employeeId, String employeeName, String title, String message) {
    try {
      EmployeeEntity emp =
          employeeId == null || employeeId.isBlank()
              ? null
              : employees.findByEmployeeId(employeeId.trim()).orElse(null);
      String email =
          emp != null && emp.getEmail() != null && !emp.getEmail().isBlank()
              ? emp.getEmail().trim()
              : null;
      String phone =
          emp != null && emp.getMobileNumber() != null
              ? emp.getMobileNumber().replaceAll("\\D", "")
              : "";
      String name =
          employeeName != null && !employeeName.isBlank()
              ? employeeName.trim()
              : (emp != null ? emp.getFullName() : "Employee");
      Map<String, String> extra = new LinkedHashMap<>();
      extra.put("title", title == null ? "HR update" : title);
      extra.put("message", message == null ? "An HR request was updated." : message);
      Long hid = audit.hospitalId();
      NotifyRequest req =
          new NotifyRequest(
              hid == null ? "10001" : String.valueOf(hid),
              "hrm-" + (employeeId == null ? "staff" : employeeId) + "-" + System.currentTimeMillis(),
              phone.isBlank() ? null : phone,
              "HRM_APPROVAL",
              name,
              phone.isBlank() ? null : phone,
              email,
              !phone.isBlank(),
              true,
              title,
              "MedTrack Hospital",
              null,
              null,
              null,
              title,
              null,
              null,
              "INR",
              extra);
      notifications.send(req);
    } catch (Exception ex) {
      log.warn("[hrm-mail] failed for {}: {}", employeeId, ex.getMessage());
    }
  }
}
