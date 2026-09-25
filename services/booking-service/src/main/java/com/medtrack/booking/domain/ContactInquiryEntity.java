package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "contact_inquiries")
public class ContactInquiryEntity {
  @Id
  @Column(name = "inquiry_id", length = 36)
  private String id;

  @Column(name = "full_name", nullable = false, length = 255)
  private String fullName;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(length = 40)
  private String phone;

  @Column(length = 255)
  private String organization;

  @Column(nullable = false, length = 255)
  private String subject;

  @Column(nullable = false, length = 4000)
  private String message;

  @Column(nullable = false, length = 32)
  private String status = "NEW";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getOrganization() {
    return organization;
  }

  public void setOrganization(String organization) {
    this.organization = organization;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
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
}
