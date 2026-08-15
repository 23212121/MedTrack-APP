package com.medtrack.booking.repo;

import com.medtrack.booking.domain.DepartmentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<DepartmentEntity, String> {
  List<DepartmentEntity> findByDoctor_DoctorIdOrderByNameAsc(String doctorId);

  List<DepartmentEntity> findByHospitalIdOrderByNameAsc(Long hospitalId);

  List<DepartmentEntity> findByHospitalIdAndFlagOrderByNameAsc(Long hospitalId, String flag);
}
