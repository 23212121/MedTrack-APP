package com.medtrack.booking.repo;

import com.medtrack.booking.domain.HospitalEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HospitalRepository extends JpaRepository<HospitalEntity, Long> {
  Optional<HospitalEntity> findByHospitalCode(String hospitalCode);

  boolean existsByHospitalCode(String hospitalCode);

  /** Highest hospital id currently stored (used to mint the next id from 10001). */
  @Query("select coalesce(max(h.id), 10000) from HospitalEntity h")
  Long findMaxId();
}
