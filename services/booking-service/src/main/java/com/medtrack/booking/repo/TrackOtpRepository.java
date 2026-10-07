package com.medtrack.booking.repo;

import com.medtrack.booking.domain.TrackOtpEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackOtpRepository extends JpaRepository<TrackOtpEntity, String> {
  Optional<TrackOtpEntity> findFirstByPhoneOrderByCreatedAtDesc(String phone);
}
