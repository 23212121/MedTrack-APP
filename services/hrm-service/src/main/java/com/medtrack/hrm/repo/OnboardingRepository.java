package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.OnboardingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingRepository extends JpaRepository<OnboardingEntity, String> {
  List<OnboardingEntity> findByDoctorIdOrderByCreationDateDesc(String doctorId);
}
