package com.medtrack.booking.repo;

import com.medtrack.booking.domain.MedicineOrderAmountHistoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicineOrderAmountHistoryRepository
    extends JpaRepository<MedicineOrderAmountHistoryEntity, String> {
  List<MedicineOrderAmountHistoryEntity> findByOrderIdOrderByVersionDesc(String orderId);
}
