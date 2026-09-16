package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.LeaveApplicationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveApplicationRepository extends JpaRepository<LeaveApplicationEntity, String> {
  List<LeaveApplicationEntity> findByHospitalIdAndDoctorIdOrderByCreationDateDesc(
      Long hospitalId, String doctorId);

  List<LeaveApplicationEntity> findByHospitalIdAndDoctorIdAndStatusOrderByCreationDateDesc(
      Long hospitalId, String doctorId, String status);

  List<LeaveApplicationEntity> findByHospitalIdAndStatusOrderByCreationDateDesc(
      Long hospitalId, String status);

  long countByHospitalIdAndDoctorIdAndStatus(Long hospitalId, String doctorId, String status);
}
