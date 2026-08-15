package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.WfhRequestEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WfhRequestRepository extends JpaRepository<WfhRequestEntity, String> {
  List<WfhRequestEntity> findByHospitalIdAndDoctorIdOrderByCreationDateDesc(
      Long hospitalId, String doctorId);
}
