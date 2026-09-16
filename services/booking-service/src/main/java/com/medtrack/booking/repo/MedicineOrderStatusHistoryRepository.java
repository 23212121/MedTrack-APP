package com.medtrack.booking.repo;

import com.medtrack.booking.domain.MedicineOrderStatusHistoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicineOrderStatusHistoryRepository
    extends JpaRepository<MedicineOrderStatusHistoryEntity, String> {
  List<MedicineOrderStatusHistoryEntity> findByOrderIdOrderByCreatedAtAsc(String orderId);
}
