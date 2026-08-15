package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.ResignationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResignationRepository extends JpaRepository<ResignationEntity, String> {
  List<ResignationEntity> findByDoctorIdOrderByResignationDateDesc(String doctorId);
}
