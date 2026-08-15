package com.medtrack.booking.repo;

import com.medtrack.booking.domain.PatientReportEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientReportRepository extends JpaRepository<PatientReportEntity, String> {
  List<PatientReportEntity> findByPatientPhoneOrderByReportDateDescCreatedAtDesc(String phone);

  /** Filter reports by phone number + patient name (both required in WHERE). */
  @Query(
      """
      SELECT r FROM PatientReportEntity r
      WHERE r.patientPhone = :phone
        AND LOWER(r.patientName) = LOWER(:patientName)
      ORDER BY r.reportDate DESC, r.createdAt DESC
      """)
  List<PatientReportEntity> findByPhoneAndPatientName(
      @Param("phone") String phone, @Param("patientName") String patientName);

  @Query(
      """
      SELECT r FROM PatientReportEntity r
      WHERE r.patientPhone = :phone
        AND (:patientName IS NULL OR LOWER(r.patientName) = LOWER(:patientName))
        AND (:from IS NULL OR r.reportDate >= :from)
        AND (:to IS NULL OR r.reportDate <= :to)
      ORDER BY r.reportDate DESC, r.createdAt DESC
      """)
  List<PatientReportEntity> search(
      @Param("phone") String phone,
      @Param("patientName") String patientName,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);
}
