package com.medtrack.visit.repo;

import com.medtrack.visit.domain.VisitEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitRepository extends JpaRepository<VisitEntity, String> {
  List<VisitEntity> findByDoctorIdAndScheduledStartBetweenOrderByScheduledStartAsc(
      String doctorId, Instant from, Instant to);

  List<VisitEntity> findByStatusInOrderByCheckedInAtAsc(List<String> statuses);

  List<VisitEntity> findByClinicIdAndDoctorIdAndStatusInOrderByScheduledStartAsc(
      String clinicId, String doctorId, List<String> statuses);

  List<VisitEntity> findByDoctorIdAndStatusInOrderByScheduledStartAsc(
      String doctorId, List<String> statuses);

  List<VisitEntity> findByDoctorIdAndStatusInOrderByTokenNumberAsc(
      String doctorId, List<String> statuses);

  Optional<VisitEntity> findTopByDoctorIdAndScheduledStartBetweenOrderByTokenNumberDesc(
      String doctorId, Instant from, Instant to);

  List<VisitEntity> findByPatientPhoneContainingOrderByCreatedAtDesc(String phone);

  /**
   * Today's live queue visits for a doctor (Asia/Kolkata day window passed as Instant range).
   * Includes check-in / in-consult / completed activity for the day.
   */
  @Query(
      """
      SELECT v FROM VisitEntity v
      WHERE v.doctorId = :doctorId
        AND v.status IN :statuses
        AND (
          (v.checkedInAt IS NOT NULL AND v.checkedInAt >= :from AND v.checkedInAt < :to)
          OR (v.actualStart IS NOT NULL AND v.actualStart >= :from AND v.actualStart < :to)
          OR (v.scheduledStart IS NOT NULL AND v.scheduledStart >= :from AND v.scheduledStart < :to
              AND v.status IN ('CHECKED_IN', 'IN_CONSULT', 'COMPLETED'))
        )
      ORDER BY CASE WHEN v.tokenNumber IS NULL THEN 999999 ELSE v.tokenNumber END ASC
      """)
  List<VisitEntity> findTodayQueueForDoctor(
      @Param("doctorId") String doctorId,
      @Param("statuses") Collection<String> statuses,
      @Param("from") Instant from,
      @Param("to") Instant to);
}
