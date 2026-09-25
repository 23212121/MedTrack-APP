package com.medtrack.booking.repo;

import com.medtrack.booking.domain.StatusEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusRepository extends JpaRepository<StatusEntity, Integer> {
  List<StatusEntity> findAllByOrderByStatusIdAsc();

  Optional<StatusEntity> findByStatusCodeIgnoreCase(String statusCode);
}
