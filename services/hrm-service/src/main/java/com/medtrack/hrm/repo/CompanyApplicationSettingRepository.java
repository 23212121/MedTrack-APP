package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.CompanyApplicationSettingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface CompanyApplicationSettingRepository
    extends JpaRepository<CompanyApplicationSettingEntity, String> {
  List<CompanyApplicationSettingEntity> findByHospitalIdAndUserId(Long hospitalId, String userId);

  boolean existsByHospitalIdAndUserIdAndRightCodeAndAllowedTrue(
      Long hospitalId, String userId, String rightCode);

  long countByHospitalIdAndUserId(Long hospitalId, String userId);

  @Modifying(clearAutomatically = true)
  void deleteByHospitalIdAndUserId(Long hospitalId, String userId);
}
