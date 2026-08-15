package com.medtrack.schedule.repo;

import com.medtrack.schedule.domain.FeeRuleEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeRuleRepository extends JpaRepository<FeeRuleEntity, String> {
  Optional<FeeRuleEntity> findByDoctorId(String doctorId);
  Optional<FeeRuleEntity> findByClinicIdAndDoctorIdIsNull(String clinicId);
}
