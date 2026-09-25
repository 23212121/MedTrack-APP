package com.medtrack.booking.repo;

import com.medtrack.booking.domain.ContactInquiryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactInquiryRepository extends JpaRepository<ContactInquiryEntity, String> {}
