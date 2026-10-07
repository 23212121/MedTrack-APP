package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "lab_technicians")
public class LabTechnicianEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "hospital_id")
  private Long hospitalId;

  @Column(name = "lab_code", nullable = false, length = 40)
  private String labCode;

  @Column(name = "lab_name", nullable = false, length = 255)
  private String labName;

  @Column(length = 32)
  private String phone;

  @Column(length = 500)
  private String address;

  @Column(length = 120)
  private String city;

  @Column(length = 120)
  private String state;

  @Column(nullable = false, length = 20)
  private String status = "ACTIVE";

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "created_by", length = 100)
  private String createdBy;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "updated_by", length = 100)
  private String updatedBy;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Long getHospitalId() {
    return hospitalId;
  }

  public void setHospitalId(Long hospitalId) {
    this.hospitalId = hospitalId;
  }

  public String getLabCode() {
    return labCode;
  }

  public void setLabCode(String labCode) {
    this.labCode = labCode;
  }

  public String getLabName() {
    return labName;
  }

  public void setLabName(String labName) {
    this.labName = labName;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }
}
