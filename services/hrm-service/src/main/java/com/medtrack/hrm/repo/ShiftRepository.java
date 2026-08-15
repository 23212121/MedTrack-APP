package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.ShiftEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<ShiftEntity, String> {
  List<ShiftEntity> findByDoctorIdOrderByNameAsc(String doctorId);
}
