package com.medtrack.booking.repo;

import com.medtrack.booking.domain.DoctorDocumentsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorDocumentsRepository extends JpaRepository<DoctorDocumentsEntity, String> {}
