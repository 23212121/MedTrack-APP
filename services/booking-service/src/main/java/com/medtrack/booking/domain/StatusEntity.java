package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Lookup for numeric status codes — svc.status (0 = Inactive, 1 = Active). */
@Entity
@Table(name = "status")
public class StatusEntity {
  @Id
  @Column(name = "status_id", nullable = false)
  private Integer statusId;

  @Column(name = "status_code", nullable = false, length = 20)
  private String statusCode;

  @Column(name = "status_name", nullable = false, length = 40)
  private String statusName;

  public Integer getStatusId() {
    return statusId;
  }

  public void setStatusId(Integer statusId) {
    this.statusId = statusId;
  }

  public String getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(String statusCode) {
    this.statusCode = statusCode;
  }

  public String getStatusName() {
    return statusName;
  }

  public void setStatusName(String statusName) {
    this.statusName = statusName;
  }
}
