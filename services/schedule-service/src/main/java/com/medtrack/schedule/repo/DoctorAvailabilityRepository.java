package com.medtrack.schedule.repo;

import com.medtrack.schedule.domain.DoctorAvailabilityEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailabilityEntity, String> {
  List<DoctorAvailabilityEntity> findByDoctorIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(
      String doctorId, Instant rangeEnd, Instant rangeStart);

  List<DoctorAvailabilityEntity> findByDoctorIdAndStartsAtBetweenOrderByStartsAtAsc(
      String doctorId, Instant from, Instant to);
}
