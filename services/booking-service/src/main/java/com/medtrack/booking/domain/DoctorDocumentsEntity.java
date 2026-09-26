package com.medtrack.booking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "doctor_documents")
public class DoctorDocumentsEntity {
  @Id
  @Column(name = "doctor_id", length = 40)
  private String doctorId;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId = 10001L;

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

  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
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
}
