package com.medtrack.schedule.repo;

import com.medtrack.schedule.domain.DoctorScheduleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorScheduleRepository extends JpaRepository<DoctorScheduleEntity, String> {
  List<DoctorScheduleEntity> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(String doctorId);
  void deleteByDoctorIdAndDayOfWeek(String doctorId, int dayOfWeek);
}
