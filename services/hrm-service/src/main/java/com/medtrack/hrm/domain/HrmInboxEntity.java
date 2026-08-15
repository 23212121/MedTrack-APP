package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "hrm_inbox",
    indexes = {
      @Index(name = "idx_hrm_inbox_hospital_doctor", columnList = "hospital_id, doctor_id"),
      @Index(name = "idx_hrm_inbox_status", columnList = "doctor_id, status, archived")
    })
public class HrmInboxEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "doctor_id", nullable = false, length = 64)
  private String doctorId;

  @Column(name = "employee_id", nullable = false, length = 40)
  private String employeeId;

  @Column(name = "employee_name", length = 160)
  private String employeeName;

  /** PERFORMANCE | LETTER_RELEASE | SALARY_INCREMENT | DOCUMENTS */
  @Column(nullable = false, length = 40)
  private String category;

  @Column(name = "document_name", nullable = false, length = 200)
  private String documentName;

  @Column(nullable = false, length = 240)
  private String title;

  /** PENDING | ACKNOWLEDGED | DOWNLOADED | ARCHIVED */
  @Column(nullable = false, length = 30)
  private String status = "PENDING";

  @Column(name = "action_required", nullable = false, length = 80)
  private String actionRequired = "Acknowledgement Required";

  @Column(name = "message_body", length = 1000)
  private String messageBody;

  @Column(name = "file_url", length = 500)
  private String fileUrl;

  @Column(name = "requested_by", length = 120)
  private String requestedBy;

  @Column(name = "requested_at")
  private Instant requestedAt;

  @Column(name = "acknowledged_at")
  private Instant acknowledgedAt;

  @Column(nullable = false)
  private Boolean archived = false;

  @Column(name = "creation_date", nullable = false)
  private Instant creationDate;

  @Column(name = "creation_user", nullable = false, length = 100)
  private String creationUser;

  @Column(name = "update_date", nullable = false)
  private Instant updateDate;

  @Column(name = "update_user", nullable = false, length = 100)
  private String updateUser;

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    if (creationDate == null) creationDate = now;
    if (updateDate == null) updateDate = now;
    if (requestedAt == null) requestedAt = now;
    if (creationUser == null || creationUser.isBlank()) creationUser = "system";
    if (updateUser == null || updateUser.isBlank()) updateUser = creationUser;
    if (status == null || status.isBlank()) status = "PENDING";
    if (archived == null) archived = false;
    if (actionRequired == null || actionRequired.isBlank()) {
      actionRequired = "Acknowledgement Required";
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updateDate = Instant.now();
    if (updateUser == null || updateUser.isBlank()) updateUser = "system";
  }

  public void touchAudit(Long hospitalId, String doctorId, String user) {
    if (hospitalId != null) this.hospitalId = hospitalId;
    if (doctorId != null && !doctorId.isBlank()) this.doctorId = doctorId;
    if (user != null && !user.isBlank()) {
      if (this.creationUser == null || this.creationUser.isBlank()) {
        this.creationUser = user;
      }
      this.updateUser = user;
    }
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public String getEmployeeId() { return employeeId; }
  public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
  public String getEmployeeName() { return employeeName; }
  public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public String getDocumentName() { return documentName; }
  public void setDocumentName(String documentName) { this.documentName = documentName; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getActionRequired() { return actionRequired; }
  public void setActionRequired(String actionRequired) { this.actionRequired = actionRequired; }
  public String getMessageBody() { return messageBody; }
  public void setMessageBody(String messageBody) { this.messageBody = messageBody; }
  public String getFileUrl() { return fileUrl; }
  public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
  public String getRequestedBy() { return requestedBy; }
  public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
  public Instant getRequestedAt() { return requestedAt; }
  public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
  public Instant getAcknowledgedAt() { return acknowledgedAt; }
  public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
  public Boolean getArchived() { return archived; }
  public void setArchived(Boolean archived) { this.archived = archived; }
  public Instant getCreationDate() { return creationDate; }
  public void setCreationDate(Instant creationDate) { this.creationDate = creationDate; }
  public String getCreationUser() { return creationUser; }
  public void setCreationUser(String creationUser) { this.creationUser = creationUser; }
  public Instant getUpdateDate() { return updateDate; }
  public void setUpdateDate(Instant updateDate) { this.updateDate = updateDate; }
  public String getUpdateUser() { return updateUser; }
  public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
}
