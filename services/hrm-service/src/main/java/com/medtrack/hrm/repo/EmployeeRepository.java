package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.EmployeeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<EmployeeEntity, String> {
  List<EmployeeEntity> findByDoctorIdOrderByEmployeeIdAsc(String doctorId);

  Optional<EmployeeEntity> findByEmployeeId(String employeeId);

  Optional<EmployeeEntity> findByDoctorLinkId(String doctorLinkId);

  long countByDoctorId(String doctorId);

  long countByDoctorIdAndEmploymentStatus(String doctorId, String status);

  long countByDoctorIdAndEmployeeType(String doctorId, String type);
}
