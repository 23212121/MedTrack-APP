package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.HrmInboxEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HrmInboxRepository extends JpaRepository<HrmInboxEntity, String> {
  List<HrmInboxEntity> findByHospitalIdAndDoctorIdAndArchivedOrderByRequestedAtDesc(
      Long hospitalId, String doctorId, Boolean archived);

  List<HrmInboxEntity> findByHospitalIdAndDoctorIdAndStatusAndArchivedOrderByRequestedAtDesc(
      Long hospitalId, String doctorId, String status, Boolean archived);

  long countByHospitalIdAndDoctorIdAndStatusAndArchived(
      Long hospitalId, String doctorId, String status, Boolean archived);

  boolean existsByHospitalIdAndDoctorIdAndCategoryAndDocumentName(
      Long hospitalId, String doctorId, String category, String documentName);
}
