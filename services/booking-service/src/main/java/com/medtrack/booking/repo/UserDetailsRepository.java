package com.medtrack.booking.repo;

import com.medtrack.booking.domain.UserDetailsEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserDetailsRepository extends JpaRepository<UserDetailsEntity, String> {
  Optional<UserDetailsEntity> findByUserId(String userId);

  Optional<UserDetailsEntity> findByUserNameIgnoreCase(String userName);

  Optional<UserDetailsEntity> findByPhone(String phone);

  boolean existsByUserNameIgnoreCase(String userName);

  boolean existsByPhone(String phone);

  /** Next sequence value for USR000001 format. */
  @Query(value = "SELECT nextval('svc.user_id_seq')", nativeQuery = true)
  Long nextUserIdSequence();
}
