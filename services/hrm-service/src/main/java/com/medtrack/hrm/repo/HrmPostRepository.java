package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.HrmPostEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HrmPostRepository extends JpaRepository<HrmPostEntity, String> {
  List<HrmPostEntity> findByHospitalIdAndDoctorIdOrderByCreationDateDesc(
      Long hospitalId, String doctorId);

  List<HrmPostEntity> findByHospitalIdOrderByCreationDateDesc(Long hospitalId);
}
