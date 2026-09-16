package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(schema = "svc", name = "hrm_interviews")
public class InterviewEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "candidate_id", nullable = false, length = 64)
  private String candidateId;

  @Column(name = "candidate_name", length = 160)
  private String candidateName;

  @Column(name = "interview_date", nullable = false)
  private LocalDate interviewDate;

  @Column(length = 120)
  private String interviewer;

  @Column(nullable = false, length = 20)
  private String status = "Hold";

  @Column(length = 500)
  private String remarks;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getCandidateId() { return candidateId; }
  public void setCandidateId(String candidateId) { this.candidateId = candidateId; }
  public String getCandidateName() { return candidateName; }
  public void setCandidateName(String candidateName) { this.candidateName = candidateName; }
  public LocalDate getInterviewDate() { return interviewDate; }
  public void setInterviewDate(LocalDate interviewDate) { this.interviewDate = interviewDate; }
  public String getInterviewer() { return interviewer; }
  public void setInterviewer(String interviewer) { this.interviewer = interviewer; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getRemarks() { return remarks; }
  public void setRemarks(String remarks) { this.remarks = remarks; }
}
