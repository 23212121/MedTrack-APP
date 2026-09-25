package com.medtrack.booking.repo;

import com.medtrack.booking.domain.EmergencyBedBookingEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyBedBookingRepository
    extends JpaRepository<EmergencyBedBookingEntity, String> {
  List<EmergencyBedBookingEntity> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);

  Optional<EmergencyBedBookingEntity> findFirstByBedIdAndStatusOrderByCreatedAtDesc(
      String bedId, String status);

  Optional<EmergencyBedBookingEntity> findByPaymentId(String paymentId);
}
