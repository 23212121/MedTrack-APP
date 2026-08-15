package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.DepartmentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HrmDepartmentRepository extends JpaRepository<DepartmentEntity, String> {
  List<DepartmentEntity> findByDoctorIdOrderByNameAsc(String doctorId);
}
