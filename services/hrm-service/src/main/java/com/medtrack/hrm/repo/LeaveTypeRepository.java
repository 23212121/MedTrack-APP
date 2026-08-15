package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.LeaveTypeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveTypeEntity, String> {
  List<LeaveTypeEntity> findByDoctorIdOrderByNameAsc(String doctorId);
}
