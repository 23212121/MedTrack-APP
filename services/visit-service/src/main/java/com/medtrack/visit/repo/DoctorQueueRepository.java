package com.medtrack.visit.repo;

import com.medtrack.visit.domain.DoctorQueueEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorQueueRepository extends JpaRepository<DoctorQueueEntity, Long> {
  Optional<DoctorQueueEntity> findByVisitId(String visitId);

  Optional<DoctorQueueEntity> findByHospitalIdAndDoctorIdAndQueueDateAndTokenNo(
      Long hospitalId, String doctorId, LocalDate queueDate, Integer tokenNo);

  List<DoctorQueueEntity> findByHospitalIdAndDoctorIdAndQueueDateOrderByTokenNoAsc(
      Long hospitalId, String doctorId, LocalDate queueDate);

  List<DoctorQueueEntity> findByHospitalIdAndDoctorIdAndQueueDateAndStatusOrderByTokenNoAsc(
      Long hospitalId, String doctorId, LocalDate queueDate, String status);

  List<DoctorQueueEntity> findByDoctorIdAndQueueDateAndStatusOrderByTokenNoAsc(
      String doctorId, LocalDate queueDate, String status);

  long countByHospitalIdAndDoctorIdAndQueueDateAndStatus(
      Long hospitalId, String doctorId, LocalDate queueDate, String status);
}
