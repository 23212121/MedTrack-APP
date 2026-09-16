package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.HolidayEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<HolidayEntity, String> {
  List<HolidayEntity> findByDoctorIdOrderByHolidayDateAsc(String doctorId);

  List<HolidayEntity> findByDoctorIdAndHolidayDateGreaterThanEqualOrderByHolidayDateAsc(
      String doctorId, LocalDate from);

  List<HolidayEntity> findByHospitalIdOrderByHolidayDateAsc(Long hospitalId);

  List<HolidayEntity> findByHospitalIdAndHolidayDateGreaterThanEqualOrderByHolidayDateAsc(
      Long hospitalId, LocalDate from);

  boolean existsByHospitalIdAndHolidayDate(Long hospitalId, LocalDate holidayDate);
}
