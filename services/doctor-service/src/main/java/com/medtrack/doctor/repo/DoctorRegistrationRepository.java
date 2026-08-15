package com.medtrack.doctor.repo;

import com.medtrack.doctor.domain.DoctorRegistrationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorRegistrationRepository
    extends JpaRepository<DoctorRegistrationEntity, String> {

  Optional<DoctorRegistrationEntity> findByDoctorId(String doctorId);

  Optional<DoctorRegistrationEntity> findByEmailIgnoreCase(String email);

  Optional<DoctorRegistrationEntity> findByUsernameIgnoreCase(String username);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByUsernameIgnoreCase(String username);

  boolean existsByMedicalRegistrationNumberIgnoreCase(String medicalRegistrationNumber);

  List<DoctorRegistrationEntity> findAllByOrderByCreatedDateDesc();
}
