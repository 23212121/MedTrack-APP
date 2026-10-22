package com.medtrack.booking.repo;

import com.medtrack.booking.domain.AmbulanceOfferEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmbulanceOfferRepository extends JpaRepository<AmbulanceOfferEntity, String> {
  List<AmbulanceOfferEntity> findByRequestId(String requestId);

  List<AmbulanceOfferEntity> findByAmbulanceIdAndStatusOrderByOfferedAtDesc(
      String ambulanceId, String status);

  List<AmbulanceOfferEntity> findByStatusAndDeadlineAtBefore(String status, Instant deadline);

  Optional<AmbulanceOfferEntity> findFirstByRequestIdAndAmbulanceIdAndStatus(
      String requestId, String ambulanceId, String status);
}
