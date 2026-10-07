package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "track_otp_config")
public class TrackOtpConfigEntity {
  @Id
  @Column(length = 32)
  private String id = "DEFAULT";

  /** Y = send OTP on public track; N = skip OTP and show details. */
  @Column(nullable = false, length = 10)
  private String flag = "Y";

  @Column(name = "updated_at")
  private Instant updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getFlag() {
    return flag;
  }

  public void setFlag(String flag) {
    this.flag = flag;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
