package com.medtrack.booking.repo;

import com.medtrack.booking.domain.MedicineOrderChargesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicineOrderChargesRepository
    extends JpaRepository<MedicineOrderChargesEntity, String> {}
