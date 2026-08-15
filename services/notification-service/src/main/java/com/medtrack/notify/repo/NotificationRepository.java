package com.medtrack.notify.repo;

import com.medtrack.notify.domain.NotificationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {
  List<NotificationEntity> findByVisitIdOrderByCreatedAtDesc(String visitId);
  List<NotificationEntity> findTop50ByOrderByCreatedAtDesc();
  boolean existsByVisitIdAndEventCodeAndChannelAndStatusIn(
      String visitId, String eventCode, String channel, List<String> statuses);
}
