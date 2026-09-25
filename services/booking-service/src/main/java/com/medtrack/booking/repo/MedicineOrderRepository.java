package com.medtrack.booking.repo;

import com.medtrack.booking.domain.MedicineOrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicineOrderRepository extends JpaRepository<MedicineOrderEntity, String> {
  Optional<MedicineOrderEntity> findByOrderNumber(String orderNumber);

  List<MedicineOrderEntity> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);

  List<MedicineOrderEntity> findByPatientPhoneOrderByCreatedAtDesc(String patientPhone);

  List<MedicineOrderEntity> findByDoctorIdOrderByCreatedAtDesc(String doctorId);

  List<MedicineOrderEntity> findByAssignedStoreIdOrderByCreatedAtDesc(String assignedStoreId);

  long countByHospitalIdAndStatus(Long hospitalId, String status);

  long countByAssignedStoreIdAndStatus(String assignedStoreId, String status);

  long countByPatientPhoneAndStatus(String patientPhone, String status);

  long countByHospitalIdAndAssignedStoreIdIsNullAndStatusIn(Long hospitalId, List<String> statuses);

  @Query(
      value =
          "SELECT MAX(order_number) FROM svc.medicine_orders WHERE order_number LIKE :prefix",
      nativeQuery = true)
  String findMaxOrderNumber(@Param("prefix") String prefix);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          UPDATE svc.medicine_orders
             SET assigned_store_id = :storeId,
                 assigned_store_name = :storeName,
                 status = 'IN_PROCESS',
                 status_code = 1,
                 updated_at = now(),
                 updated_by = :actor
           WHERE id = :orderId
             AND assigned_store_id IS NULL
             AND status IN ('ORDERED', 'PENDING')
          """,
      nativeQuery = true)
  int lockAccept(
      @Param("orderId") String orderId,
      @Param("storeId") String storeId,
      @Param("storeName") String storeName,
      @Param("actor") String actor);
}
