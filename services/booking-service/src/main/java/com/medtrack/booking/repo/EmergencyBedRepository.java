package com.medtrack.booking.repo;

import com.medtrack.booking.domain.EmergencyBedEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyBedRepository extends JpaRepository<EmergencyBedEntity, String> {
  List<EmergencyBedEntity> findByHospitalIdOrderByBedNumberAsc(Long hospitalId);

  long countByHospitalId(Long hospitalId);

  long countByHospitalIdAndStatus(Long hospitalId, String status);

  Optional<EmergencyBedEntity> findByHospitalIdAndBedNumberIgnoreCase(Long hospitalId, String bedNumber);
}
