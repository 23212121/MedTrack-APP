package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    schema = "svc",
    name = "hrm_employees",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_hrm_employee_code", columnNames = {"employee_id"}),
      @UniqueConstraint(name = "uk_hrm_employee_doctor_link", columnNames = {"doctor_link_id"})
    })
public class EmployeeEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /** Business employee code, auto-generated (e.g. EMP-1001). */
  @Column(name = "employee_id", nullable = false, length = 40)
  private String employeeId;

  /**
   * Unique link to doctor registration when employee type is Doctor.
   * Null for non-doctor staff.
   */
  @Column(name = "doctor_link_id", length = 64)
  private String doctorLinkId;

  @Column(name = "first_name", nullable = false, length = 80)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 80)
  private String lastName;

  @Column(length = 20)
  private String gender;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(name = "blood_group", length = 10)
  private String bloodGroup;

  @Column(name = "marital_status", length = 30)
  private String maritalStatus;

  @Column(length = 500)
  private String photo;

  @Column(name = "mobile_number", length = 20)
  private String mobileNumber;

  @Column(name = "alternate_number", length = 20)
  private String alternateNumber;

  @Column(length = 120)
  private String email;

  @Column(length = 300)
  private String address;

  @Column(length = 80)
  private String city;

  @Column(length = 80)
  private String state;

  @Column(name = "pin_code", length = 20)
  private String pinCode;

  @Column(name = "employee_type", nullable = false, length = 40)
  private String employeeType;

  @Column(name = "department_id", length = 64)
  private String departmentId;

  @Column(length = 120)
  private String department;

  @Column(name = "designation_id", length = 64)
  private String designationId;

  @Column(length = 120)
  private String designation;

  @Column(name = "reporting_manager", length = 120)
  private String reportingManager;

  @Column(name = "joining_date")
  private LocalDate joiningDate;

  @Column(name = "shift_id", length = 64)
  private String shiftId;

  @Column(length = 80)
  private String shift;

  @Column(name = "employment_status", nullable = false, length = 20)
  private String employmentStatus = "Active";

  public String getFullName() {
    return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getEmployeeId() { return employeeId; }
  public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
  public String getDoctorLinkId() { return doctorLinkId; }
  public void setDoctorLinkId(String doctorLinkId) { this.doctorLinkId = doctorLinkId; }
  public String getFirstName() { return firstName; }
  public void setFirstName(String firstName) { this.firstName = firstName; }
  public String getLastName() { return lastName; }
  public void setLastName(String lastName) { this.lastName = lastName; }
  public String getGender() { return gender; }
  public void setGender(String gender) { this.gender = gender; }
  public LocalDate getDateOfBirth() { return dateOfBirth; }
  public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
  public String getBloodGroup() { return bloodGroup; }
  public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }
  public String getMaritalStatus() { return maritalStatus; }
  public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }
  public String getPhoto() { return photo; }
  public void setPhoto(String photo) { this.photo = photo; }
  public String getMobileNumber() { return mobileNumber; }
  public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
  public String getAlternateNumber() { return alternateNumber; }
  public void setAlternateNumber(String alternateNumber) { this.alternateNumber = alternateNumber; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getAddress() { return address; }
  public void setAddress(String address) { this.address = address; }
  public String getCity() { return city; }
  public void setCity(String city) { this.city = city; }
  public String getState() { return state; }
  public void setState(String state) { this.state = state; }
  public String getPinCode() { return pinCode; }
  public void setPinCode(String pinCode) { this.pinCode = pinCode; }
  public String getEmployeeType() { return employeeType; }
  public void setEmployeeType(String employeeType) { this.employeeType = employeeType; }
  public String getDepartmentId() { return departmentId; }
  public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
  public String getDepartment() { return department; }
  public void setDepartment(String department) { this.department = department; }
  public String getDesignationId() { return designationId; }
  public void setDesignationId(String designationId) { this.designationId = designationId; }
  public String getDesignation() { return designation; }
  public void setDesignation(String designation) { this.designation = designation; }
  public String getReportingManager() { return reportingManager; }
  public void setReportingManager(String reportingManager) { this.reportingManager = reportingManager; }
  public LocalDate getJoiningDate() { return joiningDate; }
  public void setJoiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; }
  public String getShiftId() { return shiftId; }
  public void setShiftId(String shiftId) { this.shiftId = shiftId; }
  public String getShift() { return shift; }
  public void setShift(String shift) { this.shift = shift; }
  public String getEmploymentStatus() { return employmentStatus; }
  public void setEmploymentStatus(String employmentStatus) { this.employmentStatus = employmentStatus; }
}
