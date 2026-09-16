package com.medtrack.booking.repo;

import com.medtrack.booking.domain.MedicineOrderItemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicineOrderItemRepository extends JpaRepository<MedicineOrderItemEntity, String> {
  List<MedicineOrderItemEntity> findByOrderIdOrderBySortOrderAsc(String orderId);

  void deleteByOrderId(String orderId);
}
