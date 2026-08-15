package com.medtrack.hrm.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "hrm_employee_documents")
public class EmployeeDocumentEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "employee_pk", nullable = false, length = 64)
  private String employeePk;

  @Column(name = "employee_id", nullable = false, length = 40)
  private String employeeId;

  @Column(name = "document_type", nullable = false, length = 60)
  private String documentType;

  @Column(name = "file_name", length = 200)
  private String fileName;

  @Column(name = "file_ref", length = 500)
  private String fileRef;

  @Column(length = 300)
  private String notes;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getEmployeePk() { return employeePk; }
  public void setEmployeePk(String employeePk) { this.employeePk = employeePk; }
  public String getEmployeeId() { return employeeId; }
  public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
  public String getDocumentType() { return documentType; }
  public void setDocumentType(String documentType) { this.documentType = documentType; }
  public String getFileName() { return fileName; }
  public void setFileName(String fileName) { this.fileName = fileName; }
  public String getFileRef() { return fileRef; }
  public void setFileRef(String fileRef) { this.fileRef = fileRef; }
  public String getNotes() { return notes; }
  public void setNotes(String notes) { this.notes = notes; }
}
