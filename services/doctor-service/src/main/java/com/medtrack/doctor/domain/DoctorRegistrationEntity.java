package com.medtrack.doctor.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity for doctor self-registration.
 * Maps to table {@code doctor_details} (schema {@code svc}).
 */
@Entity
@Table(
    name = "doctor_details",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_doctor_email", columnNames = "email"),
      @UniqueConstraint(name = "uk_doctor_username", columnNames = "username"),
      @UniqueConstraint(name = "uk_doctor_med_reg", columnNames = "medical_registration_number")
    })
public class DoctorRegistrationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /** Business doctor id shown in UI (auto-generated). */
  @Column(nullable = false, unique = true, length = 40)
  private String doctorId;

  // —— 1. Personal Information ——
  @Column(nullable = false)
  private String firstName;

  private String middleName;

  @Column(nullable = false)
  private String lastName;

  @Column(nullable = false)
  private String gender;

  @Column(nullable = false)
  private LocalDate dateOfBirth;

  @Column(columnDefinition = "TEXT")
  private String profilePhoto;

  private String bloodGroup;
  private String maritalStatus;

  // —— 2. Contact Information ——
  @Column(nullable = false)
  private String mobileNumber;

  private String alternateMobileNumber;

  @Column(nullable = false)
  private String email;

  private String emergencyContactNumber;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String residentialAddress;

  @Column(nullable = false)
  private String city;

  @Column(nullable = false)
  private String state;

  @Column(nullable = false)
  private String country;

  @Column(nullable = false)
  private String postalCode;

  // —— 3. Professional Information ——
  @Column(nullable = false)
  private String medicalRegistrationNumber;

  @Column(nullable = false)
  private String medicalCouncilName;

  @Column(nullable = false)
  private LocalDate registrationDate;

  private LocalDate registrationValidUntil;
  private Integer yearsOfExperience;
  private String currentDesignation;
  private String department;

  @Column(nullable = false)
  private String specialization;

  private String subSpecialization;
  private String qualification;
  private String medicalCollege;
  private Integer graduationYear;

  // —— 4. Clinic / Hospital Information ——
  private String hospitalName;
  private String clinicName;
  private String hospitalId;
  private String branch;

  /** IN_PERSON | ONLINE | BOTH */
  private String consultationType;

  private Double consultationFee;
  private Double followUpFee;

  /** Comma-separated days e.g. Mon,Tue,Wed */
  private String availableDays;

  /** Free-text slots e.g. 09:00-13:00,14:00-18:00 */
  private String availableTimeSlots;

  // —— 5. Identity Documents ——
  private String aadhaarNumber;
  private String panNumber;
  private String passportNumber;

  @Column(columnDefinition = "TEXT")
  private String governmentIdUpload;

  // —— 6. Bank Details ——
  private String accountHolderName;
  private String bankName;
  private String accountNumber;
  private String ifscCode;
  private String upiId;

  // —— 7. Login Credentials ——
  @Column(nullable = false)
  private String username;

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @Column(nullable = false)
  private String passwordHash;

  private String securityQuestion;
  private String securityAnswerHash;

  // —— 8. Documents Upload (paths / base64 / file names) ——
  @Column(columnDefinition = "TEXT")
  private String medicalRegistrationCertificate;

  @Column(columnDefinition = "TEXT")
  private String degreeCertificate;

  @Column(columnDefinition = "TEXT")
  private String experienceCertificate;

  @Column(columnDefinition = "TEXT")
  private String identityProof;

  @Column(columnDefinition = "TEXT")
  private String addressProof;

  @Column(columnDefinition = "TEXT")
  private String passportSizePhotograph;

  @Column(columnDefinition = "TEXT")
  private String digitalSignature;

  // —— 9. Emergency Details ——
  private String emergencyContactName;
  private String emergencyRelationship;
  private String emergencyContactPhone;

  // —— 10. Professional Profile ——
  @Column(columnDefinition = "TEXT")
  private String languagesKnown;

  @Column(columnDefinition = "TEXT")
  private String biography;

  @Column(columnDefinition = "TEXT")
  private String awardsAchievements;

  @Column(columnDefinition = "TEXT")
  private String publications;

  @Column(columnDefinition = "TEXT")
  private String researchExperience;

  @Column(columnDefinition = "TEXT")
  private String memberships;

  // —— 11. System Information ——
  @Column(nullable = false)
  private String status = "ACTIVE";

  private String createdBy;

  @Column(nullable = false)
  private Instant createdDate = Instant.now();

  private Instant updatedDate = Instant.now();
  private Instant lastLogin;

  @Column(columnDefinition = "TEXT")
  private String remarks;

  // —— 12. Consent ——
  @Column(nullable = false)
  private boolean infoCorrectConfirmed;

  @Column(nullable = false)
  private boolean termsAccepted;

  @Column(nullable = false)
  private boolean privacyPolicyAccepted;

  @PreUpdate
  void onUpdate() {
    updatedDate = Instant.now();
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public String getFirstName() { return firstName; }
  public void setFirstName(String firstName) { this.firstName = firstName; }
  public String getMiddleName() { return middleName; }
  public void setMiddleName(String middleName) { this.middleName = middleName; }
  public String getLastName() { return lastName; }
  public void setLastName(String lastName) { this.lastName = lastName; }
  public String getGender() { return gender; }
  public void setGender(String gender) { this.gender = gender; }
  public LocalDate getDateOfBirth() { return dateOfBirth; }
  public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
  public String getProfilePhoto() { return profilePhoto; }
  public void setProfilePhoto(String profilePhoto) { this.profilePhoto = profilePhoto; }
  public String getBloodGroup() { return bloodGroup; }
  public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }
  public String getMaritalStatus() { return maritalStatus; }
  public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }
  public String getMobileNumber() { return mobileNumber; }
  public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
  public String getAlternateMobileNumber() { return alternateMobileNumber; }
  public void setAlternateMobileNumber(String alternateMobileNumber) {
    this.alternateMobileNumber = alternateMobileNumber;
  }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getEmergencyContactNumber() { return emergencyContactNumber; }
  public void setEmergencyContactNumber(String emergencyContactNumber) {
    this.emergencyContactNumber = emergencyContactNumber;
  }
  public String getResidentialAddress() { return residentialAddress; }
  public void setResidentialAddress(String residentialAddress) {
    this.residentialAddress = residentialAddress;
  }
  public String getCity() { return city; }
  public void setCity(String city) { this.city = city; }
  public String getState() { return state; }
  public void setState(String state) { this.state = state; }
  public String getCountry() { return country; }
  public void setCountry(String country) { this.country = country; }
  public String getPostalCode() { return postalCode; }
  public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
  public String getMedicalRegistrationNumber() { return medicalRegistrationNumber; }
  public void setMedicalRegistrationNumber(String medicalRegistrationNumber) {
    this.medicalRegistrationNumber = medicalRegistrationNumber;
  }
  public String getMedicalCouncilName() { return medicalCouncilName; }
  public void setMedicalCouncilName(String medicalCouncilName) {
    this.medicalCouncilName = medicalCouncilName;
  }
  public LocalDate getRegistrationDate() { return registrationDate; }
  public void setRegistrationDate(LocalDate registrationDate) {
    this.registrationDate = registrationDate;
  }
  public LocalDate getRegistrationValidUntil() { return registrationValidUntil; }
  public void setRegistrationValidUntil(LocalDate registrationValidUntil) {
    this.registrationValidUntil = registrationValidUntil;
  }
  public Integer getYearsOfExperience() { return yearsOfExperience; }
  public void setYearsOfExperience(Integer yearsOfExperience) {
    this.yearsOfExperience = yearsOfExperience;
  }
  public String getCurrentDesignation() { return currentDesignation; }
  public void setCurrentDesignation(String currentDesignation) {
    this.currentDesignation = currentDesignation;
  }
  public String getDepartment() { return department; }
  public void setDepartment(String department) { this.department = department; }
  public String getSpecialization() { return specialization; }
  public void setSpecialization(String specialization) { this.specialization = specialization; }
  public String getSubSpecialization() { return subSpecialization; }
  public void setSubSpecialization(String subSpecialization) {
    this.subSpecialization = subSpecialization;
  }
  public String getQualification() { return qualification; }
  public void setQualification(String qualification) { this.qualification = qualification; }
  public String getMedicalCollege() { return medicalCollege; }
  public void setMedicalCollege(String medicalCollege) { this.medicalCollege = medicalCollege; }
  public Integer getGraduationYear() { return graduationYear; }
  public void setGraduationYear(Integer graduationYear) { this.graduationYear = graduationYear; }
  public String getHospitalName() { return hospitalName; }
  public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }
  public String getClinicName() { return clinicName; }
  public void setClinicName(String clinicName) { this.clinicName = clinicName; }
  public String getHospitalId() { return hospitalId; }
  public void setHospitalId(String hospitalId) { this.hospitalId = hospitalId; }
  public String getBranch() { return branch; }
  public void setBranch(String branch) { this.branch = branch; }
  public String getConsultationType() { return consultationType; }
  public void setConsultationType(String consultationType) {
    this.consultationType = consultationType;
  }
  public Double getConsultationFee() { return consultationFee; }
  public void setConsultationFee(Double consultationFee) { this.consultationFee = consultationFee; }
  public Double getFollowUpFee() { return followUpFee; }
  public void setFollowUpFee(Double followUpFee) { this.followUpFee = followUpFee; }
  public String getAvailableDays() { return availableDays; }
  public void setAvailableDays(String availableDays) { this.availableDays = availableDays; }
  public String getAvailableTimeSlots() { return availableTimeSlots; }
  public void setAvailableTimeSlots(String availableTimeSlots) {
    this.availableTimeSlots = availableTimeSlots;
  }
  public String getAadhaarNumber() { return aadhaarNumber; }
  public void setAadhaarNumber(String aadhaarNumber) { this.aadhaarNumber = aadhaarNumber; }
  public String getPanNumber() { return panNumber; }
  public void setPanNumber(String panNumber) { this.panNumber = panNumber; }
  public String getPassportNumber() { return passportNumber; }
  public void setPassportNumber(String passportNumber) { this.passportNumber = passportNumber; }
  public String getGovernmentIdUpload() { return governmentIdUpload; }
  public void setGovernmentIdUpload(String governmentIdUpload) {
    this.governmentIdUpload = governmentIdUpload;
  }
  public String getAccountHolderName() { return accountHolderName; }
  public void setAccountHolderName(String accountHolderName) {
    this.accountHolderName = accountHolderName;
  }
  public String getBankName() { return bankName; }
  public void setBankName(String bankName) { this.bankName = bankName; }
  public String getAccountNumber() { return accountNumber; }
  public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
  public String getIfscCode() { return ifscCode; }
  public void setIfscCode(String ifscCode) { this.ifscCode = ifscCode; }
  public String getUpiId() { return upiId; }
  public void setUpiId(String upiId) { this.upiId = upiId; }
  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public String getSecurityQuestion() { return securityQuestion; }
  public void setSecurityQuestion(String securityQuestion) {
    this.securityQuestion = securityQuestion;
  }
  public String getSecurityAnswerHash() { return securityAnswerHash; }
  public void setSecurityAnswerHash(String securityAnswerHash) {
    this.securityAnswerHash = securityAnswerHash;
  }
  public String getMedicalRegistrationCertificate() { return medicalRegistrationCertificate; }
  public void setMedicalRegistrationCertificate(String medicalRegistrationCertificate) {
    this.medicalRegistrationCertificate = medicalRegistrationCertificate;
  }
  public String getDegreeCertificate() { return degreeCertificate; }
  public void setDegreeCertificate(String degreeCertificate) {
    this.degreeCertificate = degreeCertificate;
  }
  public String getExperienceCertificate() { return experienceCertificate; }
  public void setExperienceCertificate(String experienceCertificate) {
    this.experienceCertificate = experienceCertificate;
  }
  public String getIdentityProof() { return identityProof; }
  public void setIdentityProof(String identityProof) { this.identityProof = identityProof; }
  public String getAddressProof() { return addressProof; }
  public void setAddressProof(String addressProof) { this.addressProof = addressProof; }
  public String getPassportSizePhotograph() { return passportSizePhotograph; }
  public void setPassportSizePhotograph(String passportSizePhotograph) {
    this.passportSizePhotograph = passportSizePhotograph;
  }
  public String getDigitalSignature() { return digitalSignature; }
  public void setDigitalSignature(String digitalSignature) {
    this.digitalSignature = digitalSignature;
  }
  public String getEmergencyContactName() { return emergencyContactName; }
  public void setEmergencyContactName(String emergencyContactName) {
    this.emergencyContactName = emergencyContactName;
  }
  public String getEmergencyRelationship() { return emergencyRelationship; }
  public void setEmergencyRelationship(String emergencyRelationship) {
    this.emergencyRelationship = emergencyRelationship;
  }
  public String getEmergencyContactPhone() { return emergencyContactPhone; }
  public void setEmergencyContactPhone(String emergencyContactPhone) {
    this.emergencyContactPhone = emergencyContactPhone;
  }
  public String getLanguagesKnown() { return languagesKnown; }
  public void setLanguagesKnown(String languagesKnown) { this.languagesKnown = languagesKnown; }
  public String getBiography() { return biography; }
  public void setBiography(String biography) { this.biography = biography; }
  public String getAwardsAchievements() { return awardsAchievements; }
  public void setAwardsAchievements(String awardsAchievements) {
    this.awardsAchievements = awardsAchievements;
  }
  public String getPublications() { return publications; }
  public void setPublications(String publications) { this.publications = publications; }
  public String getResearchExperience() { return researchExperience; }
  public void setResearchExperience(String researchExperience) {
    this.researchExperience = researchExperience;
  }
  public String getMemberships() { return memberships; }
  public void setMemberships(String memberships) { this.memberships = memberships; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public Instant getCreatedDate() { return createdDate; }
  public void setCreatedDate(Instant createdDate) { this.createdDate = createdDate; }
  public Instant getUpdatedDate() { return updatedDate; }
  public void setUpdatedDate(Instant updatedDate) { this.updatedDate = updatedDate; }
  public Instant getLastLogin() { return lastLogin; }
  public void setLastLogin(Instant lastLogin) { this.lastLogin = lastLogin; }
  public String getRemarks() { return remarks; }
  public void setRemarks(String remarks) { this.remarks = remarks; }
  public boolean isInfoCorrectConfirmed() { return infoCorrectConfirmed; }
  public void setInfoCorrectConfirmed(boolean infoCorrectConfirmed) {
    this.infoCorrectConfirmed = infoCorrectConfirmed;
  }
  public boolean isTermsAccepted() { return termsAccepted; }
  public void setTermsAccepted(boolean termsAccepted) { this.termsAccepted = termsAccepted; }
  public boolean isPrivacyPolicyAccepted() { return privacyPolicyAccepted; }
  public void setPrivacyPolicyAccepted(boolean privacyPolicyAccepted) {
    this.privacyPolicyAccepted = privacyPolicyAccepted;
  }
}
