package com.medtrack.booking.repo;

import com.medtrack.booking.domain.LoginEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginRepository extends JpaRepository<LoginEntity, String> {
  Optional<LoginEntity> findByLoginTypeAndLoginIdIgnoreCaseAndStatus(
      String loginType, String loginId, String status);

  List<LoginEntity> findByLoginTypeAndHospitalIdAndStatus(
      String loginType, Long hospitalId, String status);

  Optional<LoginEntity> findByLoginTypeAndHospitalId(
      String loginType, Long hospitalId);
}
