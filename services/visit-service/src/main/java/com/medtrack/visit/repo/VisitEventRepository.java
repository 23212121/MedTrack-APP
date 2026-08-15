package com.medtrack.visit.repo;

import com.medtrack.visit.domain.VisitEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitEventRepository extends JpaRepository<VisitEventEntity, String> {
  List<VisitEventEntity> findByVisitIdOrderByCreatedAtAsc(String visitId);
}
