package com.medtrack.booking.repo;

import com.medtrack.booking.domain.DocumentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {
  List<DocumentEntity> findByHospitalIdOrderByCreationDateDesc(Long hospitalId);

  List<DocumentEntity> findByHospitalIdAndPhoneNumberOrderByCreationDateDesc(
      Long hospitalId, String phoneNumber);

  @Query(
      value =
          """
          SELECT * FROM svc.documents d
          WHERE regexp_replace(COALESCE(d.phone_number, ''), '[^0-9]', '', 'g') = :phone
            AND LOWER(d.patient_name) = LOWER(:patientName)
          ORDER BY d.creation_date DESC
          """,
      nativeQuery = true)
  List<DocumentEntity> findForPatientPortal(
      @Param("phone") String phone, @Param("patientName") String patientName);
}
