package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(schema = "svc", name = "hrm_holidays", uniqueConstraints = {
    @UniqueConstraint(name = "uk_hrm_holiday_date_doctor", columnNames = {"doctor_id", "holiday_date"})
})
public class HolidayEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "hospital_id")
  private Long hospitalId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "holiday_date", nullable = false)
  private LocalDate holidayDate;

  @Column(length = 400)
  private String description;

  @Column(length = 400)
  private String reason;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public LocalDate getHolidayDate() { return holidayDate; }
  public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
}
