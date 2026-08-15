package com.medtrack.booking.repo;

import com.medtrack.booking.domain.DoctorIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorIdentityRepository extends JpaRepository<DoctorIdentityEntity, String> {}
