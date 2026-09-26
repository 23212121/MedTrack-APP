package com.medtrack.booking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "doctor_bank")
public class DoctorBankEntity {
  @Id
  @Column(name = "doctor_id", length = 40)
  private String doctorId;

  @Column(name = "hospital_id", nullable = false)
  private Long hospitalId = 10001L;

  private String accountHolderName;
  private String bankName;
  private String accountNumber;
  private String ifscCode;
  private String upiId;

  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
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
}
