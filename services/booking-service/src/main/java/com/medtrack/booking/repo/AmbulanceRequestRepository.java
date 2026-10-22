package com.medtrack.booking.repo;

import com.medtrack.booking.domain.AmbulanceRequestEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmbulanceRequestRepository extends JpaRepository<AmbulanceRequestEntity, String> {
  List<AmbulanceRequestEntity> findByPatientPhoneOrderByCreatedAtDesc(String patientPhone);

  List<AmbulanceRequestEntity> findByStatusIn(Collection<String> statuses);

  long countByHospitalId(Long hospitalId);
}
