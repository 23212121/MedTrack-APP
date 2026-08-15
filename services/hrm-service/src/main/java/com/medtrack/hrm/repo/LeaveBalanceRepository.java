package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.LeaveBalanceEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalanceEntity, String> {
  List<LeaveBalanceEntity> findByDoctorIdOrderByEmployeeIdAsc(String doctorId);

  List<LeaveBalanceEntity> findByEmployeeId(String employeeId);
}
