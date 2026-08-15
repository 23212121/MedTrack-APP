package com.medtrack.booking.repo;

import com.medtrack.booking.domain.DoctorPersonalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorPersonalRepository extends JpaRepository<DoctorPersonalEntity, String> {}
