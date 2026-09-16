package com.medtrack.booking.repo;

import com.medtrack.booking.domain.MedicineOrderPharmacyResponseEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicineOrderPharmacyResponseRepository
    extends JpaRepository<MedicineOrderPharmacyResponseEntity, String> {
  List<MedicineOrderPharmacyResponseEntity> findByOrderIdOrderByCreatedAtDesc(String orderId);

  boolean existsByOrderIdAndStoreIdAndAction(String orderId, String storeId, String action);
}
