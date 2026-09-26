package com.medtrack.schedule.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "fee_rules")
public class FeeRuleEntity {
  @Id
  private String id;
  private String clinicId;
  private Long hospitalId;
  private String doctorId;
  private double baseConsultFee = 500;
  private int fixedConsultMinutes = 15;
  private double overtimeFeeAmount = 200;
  private int overtimeFeePerBlockMinutes = 15;
  private String currency = "INR";

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getClinicId() { return clinicId; }
  public void setClinicId(String clinicId) { this.clinicId = clinicId; }
  public Long getHospitalId() { return hospitalId; }

  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }

  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public double getBaseConsultFee() { return baseConsultFee; }
  public void setBaseConsultFee(double baseConsultFee) { this.baseConsultFee = baseConsultFee; }
  public int getFixedConsultMinutes() { return fixedConsultMinutes; }
  public void setFixedConsultMinutes(int fixedConsultMinutes) { this.fixedConsultMinutes = fixedConsultMinutes; }
  public double getOvertimeFeeAmount() { return overtimeFeeAmount; }
  public void setOvertimeFeeAmount(double overtimeFeeAmount) { this.overtimeFeeAmount = overtimeFeeAmount; }
  public int getOvertimeFeePerBlockMinutes() { return overtimeFeePerBlockMinutes; }
  public void setOvertimeFeePerBlockMinutes(int overtimeFeePerBlockMinutes) {
    this.overtimeFeePerBlockMinutes = overtimeFeePerBlockMinutes;
  }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
}