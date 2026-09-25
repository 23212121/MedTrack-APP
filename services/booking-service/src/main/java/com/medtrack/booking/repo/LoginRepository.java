package com.medtrack.booking.repo;

import com.medtrack.booking.domain.LoginEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginRepository extends JpaRepository<LoginEntity, String> {
  Optional<LoginEntity> findByLoginTypeAndLoginIdIgnoreCaseAndStatus(
      String loginType, String loginId, Integer status);

  List<LoginEntity> findByLoginTypeAndDoctorIdAndStatus(
      String loginType, String doctorId, Integer status);

  List<LoginEntity> findByLoginTypeAndHospitalIdAndStatus(
      String loginType, Long hospitalId, Integer status);

  List<LoginEntity> findByLoginTypeAndDoctorId(String loginType, String doctorId);

  Optional<LoginEntity> findByLoginTypeAndHospitalId(String loginType, Long hospitalId);

  Optional<LoginEntity> findByLoginTypeAndLoginIdIgnoreCase(String loginType, String loginId);
}
