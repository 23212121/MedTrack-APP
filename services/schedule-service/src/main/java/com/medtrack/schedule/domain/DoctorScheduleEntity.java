package com.medtrack.schedule.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "doctor_schedules",
    uniqueConstraints = @UniqueConstraint(columnNames = {"doctorId", "dayOfWeek", "startTime"}))
public class DoctorScheduleEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String doctorId;
  @Column(name = "hospital_id")
  private Long hospitalId;
  private int dayOfWeek;
  private String startTime;
  private String endTime;
  private int slotMinutes = 15;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getDoctorId() { return doctorId; }
  public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public int getDayOfWeek() { return dayOfWeek; }
  public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
  public String getStartTime() { return startTime; }
  public void setStartTime(String startTime) { this.startTime = startTime; }
  public String getEndTime() { return endTime; }
  public void setEndTime(String endTime) { this.endTime = endTime; }
  public int getSlotMinutes() { return slotMinutes; }
  public void setSlotMinutes(int slotMinutes) { this.slotMinutes = slotMinutes; }
}
