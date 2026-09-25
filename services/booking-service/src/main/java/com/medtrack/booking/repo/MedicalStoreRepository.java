package com.medtrack.booking.repo;

import com.medtrack.booking.domain.MedicalStoreEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalStoreRepository extends JpaRepository<MedicalStoreEntity, String> {
  Optional<MedicalStoreEntity> findByStoreCodeIgnoreCase(String storeCode);

  List<MedicalStoreEntity> findByHospitalIdOrderByStoreNameAsc(Long hospitalId);

  List<MedicalStoreEntity> findByHospitalIdAndStatusOrderByStoreNameAsc(Long hospitalId, String status);

  List<MedicalStoreEntity> findByStatusOrderByStoreNameAsc(String status);

  List<MedicalStoreEntity> findAllByOrderByStoreNameAsc();

  long countByHospitalId(Long hospitalId);
}
