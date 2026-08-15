package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.DesignationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignationRepository extends JpaRepository<DesignationEntity, String> {
  List<DesignationEntity> findByDoctorIdOrderByNameAsc(String doctorId);
}
