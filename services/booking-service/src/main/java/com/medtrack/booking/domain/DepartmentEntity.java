package com.medtrack.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** JPA entity → table {@code user} (doctor_id FK → doctor_personal.doctor_id). */
@Entity
@Table(name = "\"user\"")
public class DepartmentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String value;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "user_creation")
  private String userCreation;

  @Column(name = "update_date")
  private Instant updateDate;

  @Column(name = "update_user")
  private String updateUser;

  @Column(name = "creation_date", nullable = false)
  private Instant creationDate = Instant.now();

  @Column(nullable = false, length = 10)
  private String flag = "Y";

  @Column(name = "hospital_id")
  private Long hospitalId;

  /** Foreign key to doctor_personal.doctor_id */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "doctor_id",
      referencedColumnName = "doctor_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_user_doctor"))
  private DoctorPersonalEntity doctor;

  @PrePersist
  void onCreate() {
    if (creationDate == null) {
      creationDate = Instant.now();
    }
    updateDate = Instant.now();
  }

  @PreUpdate
  void onUpdate() {
    updateDate = Instant.now();
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getValue() { return value; }
  public void setValue(String value) { this.value = value; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getUserCreation() { return userCreation; }
  public void setUserCreation(String userCreation) { this.userCreation = userCreation; }
  public Instant getUpdateDate() { return updateDate; }
  public void setUpdateDate(Instant updateDate) { this.updateDate = updateDate; }
  public String getUpdateUser() { return updateUser; }
  public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
  public Instant getCreationDate() { return creationDate; }
  public void setCreationDate(Instant creationDate) { this.creationDate = creationDate; }
  public String getFlag() { return flag; }
  public void setFlag(String flag) { this.flag = flag; }
  public Long getHospitalId() { return hospitalId; }
  public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
  public DoctorPersonalEntity getDoctor() { return doctor; }
  public void setDoctor(DoctorPersonalEntity doctor) { this.doctor = doctor; }
}
