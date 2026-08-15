package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.SalaryStructureEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryStructureRepository extends JpaRepository<SalaryStructureEntity, String> {
  List<SalaryStructureEntity> findByDoctorIdOrderByEmployeeIdAsc(String doctorId);

  Optional<SalaryStructureEntity> findByEmployeeIdAndDoctorId(String employeeId, String doctorId);
}
