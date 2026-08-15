package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.PayslipEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipRepository extends JpaRepository<PayslipEntity, String> {
  List<PayslipEntity> findByDoctorIdOrderByPayYearDescPayMonthDesc(String doctorId);

  Optional<PayslipEntity> findByEmployeeIdAndPayMonthAndPayYearAndDoctorId(
      String employeeId, Integer month, Integer year, String doctorId);
}
