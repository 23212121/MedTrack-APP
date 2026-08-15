package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.TrainingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingRepository extends JpaRepository<TrainingEntity, String> {
  List<TrainingEntity> findByDoctorIdOrderByStartDateDesc(String doctorId);
}
