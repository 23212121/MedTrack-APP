package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.PerformanceReviewEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReviewEntity, String> {
  List<PerformanceReviewEntity> findByDoctorIdOrderByReviewDateDesc(String doctorId);
}
