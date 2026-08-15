package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.InterviewEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<InterviewEntity, String> {
  List<InterviewEntity> findByDoctorIdOrderByInterviewDateDesc(String doctorId);
}
