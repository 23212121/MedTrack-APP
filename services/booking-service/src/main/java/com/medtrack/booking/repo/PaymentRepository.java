package com.medtrack.booking.repo;

import com.medtrack.booking.domain.PaymentEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {
  Optional<PaymentEntity> findByGatewayTransactionId(String gatewayTransactionId);

  Optional<PaymentEntity> findByGatewayOrderId(String gatewayOrderId);

  Optional<PaymentEntity> findByGatewayQrId(String gatewayQrId);

  List<PaymentEntity> findByAppointmentIdOrderByCreatedAtDesc(String appointmentId);

  List<PaymentEntity> findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
      String referenceType, String referenceId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PaymentEntity p where p.paymentId = :id")
  Optional<PaymentEntity> lockById(@Param("id") String id);
}
