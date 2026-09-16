package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(schema = "svc", name = "hrm_assets")
public class AssetEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "employee_pk", length = 64)
  private String employeePk;

  @Column(name = "employee_id", length = 40)
  private String employeeId;

  @Column(name = "employee_name", length = 160)
  private String employeeName;

  @Column(name = "asset_type", nullable = false, length = 60)
  private String assetType;

  @Column(name = "asset_name", length = 160)
  private String assetName;

  @Column(name = "serial_number", length = 80)
  private String serialNumber;

  @Column(name = "allocated_date")
  private LocalDate allocatedDate;

  @Column(length = 40)
  private String status = "Allocated";

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getEmployeePk() { return employeePk; }
  public void setEmployeePk(String employeePk) { this.employeePk = employeePk; }
  public String getEmployeeId() { return employeeId; }
  public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
  public String getEmployeeName() { return employeeName; }
  public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
  public String getAssetType() { return assetType; }
  public void setAssetType(String assetType) { this.assetType = assetType; }
  public String getAssetName() { return assetName; }
  public void setAssetName(String assetName) { this.assetName = assetName; }
  public String getSerialNumber() { return serialNumber; }
  public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
  public LocalDate getAllocatedDate() { return allocatedDate; }
  public void setAllocatedDate(LocalDate allocatedDate) { this.allocatedDate = allocatedDate; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
