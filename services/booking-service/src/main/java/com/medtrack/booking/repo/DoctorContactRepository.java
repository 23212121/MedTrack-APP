package com.medtrack.booking.repo;

import com.medtrack.booking.domain.DoctorContactEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorContactRepository extends JpaRepository<DoctorContactEntity, String> {
  Optional<DoctorContactEntity> findFirstByEmailIgnoreCase(String email);
}
