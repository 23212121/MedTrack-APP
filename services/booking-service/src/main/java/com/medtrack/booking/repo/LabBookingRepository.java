package com.medtrack.booking.repo;

import com.medtrack.booking.domain.LabBookingEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabBookingRepository extends JpaRepository<LabBookingEntity, String> {
  Optional<LabBookingEntity> findByBookingNumberIgnoreCase(String bookingNumber);

  List<LabBookingEntity> findByPatientPhoneOrderByCreatedAtDesc(String patientPhone);

  List<LabBookingEntity> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);

  List<LabBookingEntity> findByHospitalIdAndStatusOrderByCreatedAtDesc(Long hospitalId, String status);

  List<LabBookingEntity> findByAssignedLabIdOrderByCreatedAtDesc(String assignedLabId);

  List<LabBookingEntity> findByHospitalIdAndStatusInOrderByCreatedAtDesc(
      Long hospitalId, Collection<String> statuses);

  List<LabBookingEntity> findByHospitalIdAndAssignedLabIdAndQueueDateAndStatusInOrderByTokenNumberAsc(
      Long hospitalId, String assignedLabId, LocalDate queueDate, Collection<String> statuses);

  List<LabBookingEntity> findByHospitalIdAndQueueDateAndModeAndStatusInOrderByCreatedAtAsc(
      Long hospitalId, LocalDate queueDate, String mode, Collection<String> statuses);

  long countByHospitalIdAndQueueDateAndModeAndStatusIn(
      Long hospitalId, LocalDate queueDate, String mode, Collection<String> statuses);
}
