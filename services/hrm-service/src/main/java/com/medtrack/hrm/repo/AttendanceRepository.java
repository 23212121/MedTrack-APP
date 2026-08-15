package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.AttendanceEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<AttendanceEntity, String> {
  List<AttendanceEntity> findByDoctorIdOrderByAttendanceDateDesc(String doctorId);

  List<AttendanceEntity> findByDoctorIdAndAttendanceDate(String doctorId, LocalDate date);

  long countByDoctorIdAndAttendanceDateAndStatus(String doctorId, LocalDate date, String status);
}
