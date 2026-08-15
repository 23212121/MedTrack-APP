package com.medtrack.visit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "doctor_queue",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_doctor_queue_day_token",
            columnNames = {"hospital_id", "doctor_id", "queue_date", "token_no"}))
public class DoctorQueueEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "doctor_id", nullable = false, length = 64)
  private String doctorId;

  @Column(name = "token_no", nullable = false)
  private Integer tokenNo;

  @Column(name = "patient_id", length = 64)
  private String patientId;

  @Column(name = "patient_name", length = 200)
  private String patientName;

  @Column(name = "patient_phone", length = 40)
  private String patientPhone;

  @Column(name = "visit_id", length = 64)
  private String visitId;

  /** WAITING | RUNNING | COMPLETED | CANCELLED */
  @Column(nullable = false, length = 20)
  private String status = "WAITING";

  @Column(name = "checkin_time")
  private Instant checkinTime;

  @Column(name = "consultation_start")
  private Instant consultationStart;

  @Column(name = "consultation_end")
  private Instant consultationEnd;

  @Column(name = "queue_date", nullable = false)
  private LocalDate queueDate;

  @Column(name = "sms_two_ahead_sent", nullable = false)
  private boolean smsTwoAheadSent = false;

  @Column(name = "doctor_name", length = 200)
  private String doctorName;

  @Column(name = "department", length = 120)
  private String department;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getHospitalId() {
    return hospitalId;
  }

  public void setHospitalId(Long hospitalId) {
    this.hospitalId = hospitalId;
  }

  public String getDoctorId() {
    return doctorId;
  }

  public void setDoctorId(String doctorId) {
    this.doctorId = doctorId;
  }

  public Integer getTokenNo() {
    return tokenNo;
  }

  public void setTokenNo(Integer tokenNo) {
    this.tokenNo = tokenNo;
  }

  public String getPatientId() {
    return patientId;
  }

  public void setPatientId(String patientId) {
    this.patientId = patientId;
  }

  public String getPatientName() {
    return patientName;
  }

  public void setPatientName(String patientName) {
    this.patientName = patientName;
  }

  public String getPatientPhone() {
    return patientPhone;
  }

  public void setPatientPhone(String patientPhone) {
    this.patientPhone = patientPhone;
  }

  public String getVisitId() {
    return visitId;
  }

  public void setVisitId(String visitId) {
    this.visitId = visitId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getCheckinTime() {
    return checkinTime;
  }

  public void setCheckinTime(Instant checkinTime) {
    this.checkinTime = checkinTime;
  }

  public Instant getConsultationStart() {
    return consultationStart;
  }

  public void setConsultationStart(Instant consultationStart) {
    this.consultationStart = consultationStart;
  }

  public Instant getConsultationEnd() {
    return consultationEnd;
  }

  public void setConsultationEnd(Instant consultationEnd) {
    this.consultationEnd = consultationEnd;
  }

  public LocalDate getQueueDate() {
    return queueDate;
  }

  public void setQueueDate(LocalDate queueDate) {
    this.queueDate = queueDate;
  }

  public boolean isSmsTwoAheadSent() {
    return smsTwoAheadSent;
  }

  public void setSmsTwoAheadSent(boolean smsTwoAheadSent) {
    this.smsTwoAheadSent = smsTwoAheadSent;
  }

  public String getDoctorName() {
    return doctorName;
  }

  public void setDoctorName(String doctorName) {
    this.doctorName = doctorName;
  }

  public String getDepartment() {
    return department;
  }

  public void setDepartment(String department) {
    this.department = department;
  }
}
