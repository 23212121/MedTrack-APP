package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.CandidateEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<CandidateEntity, String> {
  List<CandidateEntity> findByDoctorIdOrderByCreationDateDesc(String doctorId);
}
