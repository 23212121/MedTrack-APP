package com.medtrack.booking.repo;

import com.medtrack.booking.domain.MedicineOrderNotificationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicineOrderNotificationRepository
    extends JpaRepository<MedicineOrderNotificationEntity, String> {
  List<MedicineOrderNotificationEntity> findByStoreIdOrderByCreatedAtDesc(String storeId);

  List<MedicineOrderNotificationEntity> findByHospitalIdAndAudienceOrderByCreatedAtDesc(
      Long hospitalId, String audience);

  List<MedicineOrderNotificationEntity> findByPatientPhoneOrderByCreatedAtDesc(String patientPhone);

  long countByStoreIdAndReadFlagFalse(String storeId);

  long countByPatientPhoneAndReadFlagFalse(String patientPhone);
}
