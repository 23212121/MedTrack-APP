package com.medtrack.booking.repo;

import com.medtrack.booking.domain.LabBookingChatEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabBookingChatRepository extends JpaRepository<LabBookingChatEntity, String> {
  List<LabBookingChatEntity> findByBookingIdOrderByCreatedAtAsc(String bookingId);
}
