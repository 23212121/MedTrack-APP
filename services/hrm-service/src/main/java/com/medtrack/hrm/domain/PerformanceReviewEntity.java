package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "hrm_performance_reviews")
public class PerformanceReviewEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "employee_pk", nullable = false, length = 64)
  private String employeePk;

  @Column(name = "employee_id", nullable = false, length = 40)
  private String employeeId;

  @Column(name = "employee_name", length = 160)
  private String employeeName;

  @Column(name = "review_date", nullable = false)
  private LocalDate reviewDate;

  @Column(nullable = false)
  private Integer rating;

  @Column(length = 1000)
  private String strengths;

  @Column(length = 1000)
  private String improvements;

  @Column(name = "manager_remarks", length = 1000)
  private String managerRemarks;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getEmployeePk() { return employeePk; }
  public void setEmployeePk(String employeePk) { this.employeePk = employeePk; }
  public String getEmployeeId() { return employeeId; }
  public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
  public String getEmployeeName() { return employeeName; }
  public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
  public LocalDate getReviewDate() { return reviewDate; }
  public void setReviewDate(LocalDate reviewDate) { this.reviewDate = reviewDate; }
  public Integer getRating() { return rating; }
  public void setRating(Integer rating) { this.rating = rating; }
  public String getStrengths() { return strengths; }
  public void setStrengths(String strengths) { this.strengths = strengths; }
  public String getImprovements() { return improvements; }
  public void setImprovements(String improvements) { this.improvements = improvements; }
  public String getManagerRemarks() { return managerRemarks; }
  public void setManagerRemarks(String managerRemarks) { this.managerRemarks = managerRemarks; }
}
