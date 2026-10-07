package com.medtrack.booking.repo;

import com.medtrack.booking.domain.DocumentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {
  List<DocumentEntity> findByHospitalIdOrderByCreationDateDesc(Long hospitalId);

  List<DocumentEntity> findTop200ByOrderByCreationDateDesc();

  List<DocumentEntity> findByHospitalIdAndPhoneNumberOrderByCreationDateDesc(
      Long hospitalId, String phoneNumber);

  List<DocumentEntity> findByLabIdOrderByCreationDateDesc(String labId);

  List<DocumentEntity> findByAadhaarNumberOrderByCreationDateDesc(String aadhaarNumber);

  @Query(
      value =
          """
          SELECT * FROM svc.documents d
          WHERE regexp_replace(COALESCE(d.phone_number, ''), '[^0-9]', '', 'g') = :phone
            AND (
              :patientName IS NULL
              OR TRIM(:patientName) = ''
              OR LOWER(d.patient_name) = LOWER(:patientName)
              OR UPPER(COALESCE(d.document_type, '')) IN ('LAB', 'LAB_REPORT')
            )
          ORDER BY d.creation_date DESC
          """,
      nativeQuery = true)
  List<DocumentEntity> findForPatientPortal(
      @Param("phone") String phone, @Param("patientName") String patientName);

  @Query(
      value =
          """
          SELECT * FROM svc.documents d
          WHERE regexp_replace(COALESCE(d.phone_number, ''), '[^0-9]', '', 'g') = :phone
          ORDER BY d.creation_date DESC
          """,
      nativeQuery = true)
  List<DocumentEntity> findForPatientPortalByPhone(@Param("phone") String phone);

  @Query(
      value =
          """
          SELECT * FROM svc.documents d
          WHERE regexp_replace(COALESCE(d.aadhaar_number, ''), '[^0-9]', '', 'g') = :aadhaar
          ORDER BY d.creation_date DESC
          """,
      nativeQuery = true)
  List<DocumentEntity> findForAadhaar(@Param("aadhaar") String aadhaar);
}
