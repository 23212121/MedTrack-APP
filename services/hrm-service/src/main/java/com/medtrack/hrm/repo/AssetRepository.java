package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.AssetEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<AssetEntity, String> {
  List<AssetEntity> findByDoctorIdOrderByAllocatedDateDesc(String doctorId);
}
