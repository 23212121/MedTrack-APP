package com.medtrack.booking.repo;

import com.medtrack.booking.domain.DoctorProfessionalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorProfessionalRepository
    extends JpaRepository<DoctorProfessionalEntity, String> {}
