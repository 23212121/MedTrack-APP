package com.medtrack.booking.repo;

import com.medtrack.booking.domain.LabTechnicianEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabTechnicianRepository extends JpaRepository<LabTechnicianEntity, String> {
  Optional<LabTechnicianEntity> findByLabCodeIgnoreCase(String labCode);

  List<LabTechnicianEntity> findByHospitalIdOrderByLabNameAsc(Long hospitalId);

  List<LabTechnicianEntity> findByHospitalIdAndStatusOrderByLabNameAsc(Long hospitalId, String status);

  List<LabTechnicianEntity> findByStatusOrderByLabNameAsc(String status);

  List<LabTechnicianEntity> findAllByOrderByLabNameAsc();

  long countByHospitalId(Long hospitalId);
}
