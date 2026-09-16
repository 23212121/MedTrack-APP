package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
    name = "care_chats",
    indexes = {
      @Index(name = "idx_care_chats_appt", columnList = "appointment_id"),
      @Index(name = "idx_care_chats_phone", columnList = "patient_phone")
    })
public class CareChatEntity {
  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "appointment_id", length = 64)
  private String appointmentId;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "doctor_id", nullable = false, length = 64)
  private String doctorId;

  @Column(name = "patient_name", nullable = false, length = 255)
  private String patientName;

  @Column(name = "patient_phone", length = 32)
  private String patientPhone;

  @Column(name = "sender_type", nullable = false, length = 20)
  private String senderType;

  @Column(name = "sender_name", length = 255)
  private String senderName;

  @Column(name = "message_text", nullable = false)
  private String messageText;

  @Column(name = "document_url", length = 1000)
  private String documentUrl;

  @Column(name = "document_name", length = 255)
  private String documentName;

  @Column(name = "created_date", nullable = false)
  private Instant createdDate = Instant.now();

  @Column(name = "created_user", length = 100)
  private String createdUser;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getAppointmentId() { return appointmentId; }
  public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public String getPatientName() { return patientName; }
  public void setPatientName(String patientName) { this.patientName = patientName; }
  public String getPatientPhone() { return patientPhone; }
  public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
  public String getSenderType() { return senderType; }
  public void setSenderType(String senderType) { this.senderType = senderType; }
  public String getSenderName() { return senderName; }
  public void setSenderName(String senderName) { this.senderName = senderName; }
  public String getMessageText() { return messageText; }
  public void setMessageText(String messageText) { this.messageText = messageText; }
  public String getDocumentUrl() { return documentUrl; }
  public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }
  public String getDocumentName() { return documentName; }
  public void setDocumentName(String documentName) { this.documentName = documentName; }
  public Instant getCreatedDate() { return createdDate; }
  public void setCreatedDate(Instant createdDate) { this.createdDate = createdDate; }
  public String getCreatedUser() { return createdUser; }
  public void setCreatedUser(String createdUser) { this.createdUser = createdUser; }
}
