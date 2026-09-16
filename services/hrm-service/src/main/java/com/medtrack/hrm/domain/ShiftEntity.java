package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(schema = "svc", name = "hrm_shifts", uniqueConstraints = {
    @UniqueConstraint(name = "uk_hrm_shift_name_doctor", columnNames = {"doctor_id", "name"})
})
public class ShiftEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  @Column(name = "break_duration_minutes")
  private Integer breakDurationMinutes = 30;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public LocalTime getStartTime() { return startTime; }
  public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
  public LocalTime getEndTime() { return endTime; }
  public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
  public Integer getBreakDurationMinutes() { return breakDurationMinutes; }
  public void setBreakDurationMinutes(Integer breakDurationMinutes) {
    this.breakDurationMinutes = breakDurationMinutes;
  }
}
