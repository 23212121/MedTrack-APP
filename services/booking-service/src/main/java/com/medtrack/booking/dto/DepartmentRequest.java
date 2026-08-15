package com.medtrack.booking.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for POST/PUT /api/departments. */
public class DepartmentRequest {

  @NotBlank(message = "doctorId is required (FK to doctor_details)")
  private String doctorId;

  @NotBlank(message = "name is required")
  private String name;

  @NotBlank(message = "value is required")
  private String value;

  private String description;
  private String userCreation;
  private String updateUser;
  private String flag;
  private Long hospitalId;

  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getValue() { return value; }
  public void setValue(String value) { this.value = value; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getUserCreation() { return userCreation; }
  public void setUserCreation(String userCreation) { this.userCreation = userCreation; }
  public String getUpdateUser() { return updateUser; }
  public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
  public String getFlag() { return flag; }
  public void setFlag(String flag) { this.flag = flag; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
}
