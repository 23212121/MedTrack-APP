package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.EmployeeDocumentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocumentEntity, String> {
  List<EmployeeDocumentEntity> findByDoctorIdOrderByCreationDateDesc(String doctorId);

  List<EmployeeDocumentEntity> findByEmployeeId(String employeeId);
}
