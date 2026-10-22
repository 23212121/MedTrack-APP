package com.medtrack.booking.repo;

import com.medtrack.booking.domain.AmbulanceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmbulanceRepository extends JpaRepository<AmbulanceEntity, String> {
  Optional<AmbulanceEntity> findByMobileNumber(String mobileNumber);

  List<AmbulanceEntity> findByAvailabilityStatus(String availabilityStatus);

  List<AmbulanceEntity> findByHospitalIdOrderByDriverNameAsc(Long hospitalId);

  List<AmbulanceEntity> findAllByOrderByDriverNameAsc();
}
