package com.medtrack.booking.repo;

import com.medtrack.booking.domain.DoctorBankEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorBankRepository extends JpaRepository<DoctorBankEntity, String> {}
