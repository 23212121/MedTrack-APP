package com.medtrack.booking.repo;

import com.medtrack.booking.domain.MedicineOrderDocumentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicineOrderDocumentRepository
    extends JpaRepository<MedicineOrderDocumentEntity, String> {
  List<MedicineOrderDocumentEntity> findByOrderIdOrderByCreatedAtDesc(String orderId);

  @Modifying
  @Query("UPDATE MedicineOrderDocumentEntity d SET d.latest = false WHERE d.orderId = :orderId")
  void clearLatest(@Param("orderId") String orderId);
}
