package com.medtrack.hrm.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(schema = "svc", name = "hrm_trainings")
public class TrainingEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 120)
  private String trainer;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(length = 1000)
  private String participants;

  @Column(length = 400)
  private String description;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getTrainer() { return trainer; }
  public void setTrainer(String trainer) { this.trainer = trainer; }
  public LocalDate getStartDate() { return startDate; }
  public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
  public LocalDate getEndDate() { return endDate; }
  public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
  public String getParticipants() { return participants; }
  public void setParticipants(String participants) { this.participants = participants; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
}
