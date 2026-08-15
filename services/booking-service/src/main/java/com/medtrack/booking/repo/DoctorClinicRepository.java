package com.medtrack.booking.repo;

import com.medtrack.booking.domain.DoctorClinicEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorClinicRepository extends JpaRepository<DoctorClinicEntity, String> {
  List<DoctorClinicEntity> findByHospitalIdOrderByHospitalNameAsc(Long hospitalId);
}
