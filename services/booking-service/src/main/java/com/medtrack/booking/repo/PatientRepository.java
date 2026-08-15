package com.medtrack.booking.repo;

import com.medtrack.booking.domain.PatientEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<PatientEntity, String> {
  Optional<PatientEntity> findByPhone(String phone);

  boolean existsByPhone(String phone);
}
