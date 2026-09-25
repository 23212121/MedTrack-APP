package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "medicine_order_documents")
public class MedicineOrderDocumentEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "order_id", nullable = false)
  private String orderId;

  @Column(name = "file_name", nullable = false)
  private String fileName;

  @Column(name = "content_type")
  private String contentType;

  @Column(name = "file_path", nullable = false)
  private String filePath;

  private boolean latest = true;

  @Column(length = 32)
  private String kind = "PRESCRIPTION";

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "created_by")
  private String createdBy;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getOrderId() { return orderId; }
  public void setOrderId(String orderId) { this.orderId = orderId; }
  public String getFileName() { return fileName; }
  public void setFileName(String fileName) { this.fileName = fileName; }
  public String getContentType() { return contentType; }
  public void setContentType(String contentType) { this.contentType = contentType; }
  public String getFilePath() { return filePath; }
  public void setFilePath(String filePath) { this.filePath = filePath; }
  public boolean isLatest() { return latest; }
  public void setLatest(boolean latest) { this.latest = latest; }
  public String getKind() { return kind; }
  public void setKind(String kind) { this.kind = kind; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
