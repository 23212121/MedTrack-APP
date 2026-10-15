package com.medtrack.booking.repo;

import com.medtrack.booking.domain.LabBookingNotificationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabBookingNotificationRepository
    extends JpaRepository<LabBookingNotificationEntity, String> {
  List<LabBookingNotificationEntity> findByLabIdOrderByCreatedAtDesc(String labId);

  List<LabBookingNotificationEntity> findByPatientPhoneOrderByCreatedAtDesc(String patientPhone);

  List<LabBookingNotificationEntity> findByHospitalIdAndAudienceOrderByCreatedAtDesc(
      Long hospitalId, String audience);
}
