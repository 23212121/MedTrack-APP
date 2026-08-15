package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.LeaveRequestEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequestEntity, String> {
  List<LeaveRequestEntity> findByDoctorIdOrderByCreationDateDesc(String doctorId);

  List<LeaveRequestEntity> findByDoctorIdAndStatus(String doctorId, String status);

  long countByDoctorIdAndStatus(String doctorId, String status);
}
