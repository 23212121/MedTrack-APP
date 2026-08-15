package com.medtrack.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "documents")
public class DocumentEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "patient_name", nullable = false, length = 255)
  private String patientName;

  @Column(name = "aadhaar_number", length = 20)
  private String aadhaarNumber;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId;

  @Column(name = "phone_number", nullable = false, length = 32)
  private String phoneNumber;

  /** e.g. TEST / LAB / REPORT */
  @Column(name = "document_type", length = 64)
  private String documentType;

  @Column(name = "file_path", length = 1000)
  private String filePath;

  @Column(name = "destination_path", length = 1000)
  private String destinationPath;

  @Column(name = "source_path", length = 2000)
  private String sourcePath;

  @Column(name = "file_upload_1", length = 1000)
  private String fileUpload1;

  @Column(name = "file_upload_2", length = 1000)
  private String fileUpload2;

  @Column(name = "file_upload_3", length = 1000)
  private String fileUpload3;

  @Column(name = "file_upload_4", length = 1000)
  private String fileUpload4;

  @Column(name = "file_upload_5", length = 1000)
  private String fileUpload5;

  @Column(name = "creation_date", nullable = false)
  private Instant creationDate = Instant.now();

  @Column(name = "creation_user", length = 100)
  private String creationUser;

  @Column(name = "update_date")
  private Instant updateDate;

  @Column(name = "update_user", length = 100)
  private String updateUser;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPatientName() {
    return patientName;
  }

  public void setPatientName(String patientName) {
    this.patientName = patientName;
  }

  public String getAadhaarNumber() {
    return aadhaarNumber;
  }

  public void setAadhaarNumber(String aadhaarNumber) {
    this.aadhaarNumber = aadhaarNumber;
  }

  public Long getHospitalId() {
    return hospitalId;
  }

  public void setHospitalId(Long hospitalId) {
    this.hospitalId = hospitalId;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public String getDocumentType() {
    return documentType;
  }

  public void setDocumentType(String documentType) {
    this.documentType = documentType;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getDestinationPath() {
    return destinationPath;
  }

  public void setDestinationPath(String destinationPath) {
    this.destinationPath = destinationPath;
  }

  public String getSourcePath() {
    return sourcePath;
  }

  public void setSourcePath(String sourcePath) {
    this.sourcePath = sourcePath;
  }

  public String getFileUpload1() {
    return fileUpload1;
  }

  public void setFileUpload1(String fileUpload1) {
    this.fileUpload1 = fileUpload1;
  }

  public String getFileUpload2() {
    return fileUpload2;
  }

  public void setFileUpload2(String fileUpload2) {
    this.fileUpload2 = fileUpload2;
  }

  public String getFileUpload3() {
    return fileUpload3;
  }

  public void setFileUpload3(String fileUpload3) {
    this.fileUpload3 = fileUpload3;
  }

  public String getFileUpload4() {
    return fileUpload4;
  }

  public void setFileUpload4(String fileUpload4) {
    this.fileUpload4 = fileUpload4;
  }

  public String getFileUpload5() {
    return fileUpload5;
  }

  public void setFileUpload5(String fileUpload5) {
    this.fileUpload5 = fileUpload5;
  }

  public Instant getCreationDate() {
    return creationDate;
  }

  public void setCreationDate(Instant creationDate) {
    this.creationDate = creationDate;
  }

  public String getCreationUser() {
    return creationUser;
  }

  public void setCreationUser(String creationUser) {
    this.creationUser = creationUser;
  }

  public Instant getUpdateDate() {
    return updateDate;
  }

  public void setUpdateDate(Instant updateDate) {
    this.updateDate = updateDate;
  }

  public String getUpdateUser() {
    return updateUser;
  }

  public void setUpdateUser(String updateUser) {
    this.updateUser = updateUser;
  }
}
