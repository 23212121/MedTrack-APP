package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "lab_booking_chats")
public class LabBookingChatEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "booking_id", nullable = false, length = 64)
  private String bookingId;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "sender_type", nullable = false, length = 20)
  private String senderType;

  @Column(name = "sender_name")
  private String senderName;

  @Column(name = "message_text", nullable = false, length = 2000)
  private String messageText;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "created_by", length = 100)
  private String createdBy;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getBookingId() { return bookingId; }
  public void setBookingId(String bookingId) { this.bookingId = bookingId; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getSenderType() { return senderType; }
  public void setSenderType(String senderType) { this.senderType = senderType; }
  public String getSenderName() { return senderName; }
  public void setSenderName(String senderName) { this.senderName = senderName; }
  public String getMessageText() { return messageText; }
  public void setMessageText(String messageText) { this.messageText = messageText; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
