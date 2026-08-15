package com.medtrack.booking.repo;

import com.medtrack.booking.domain.BookingEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<BookingEntity, String> {
  List<BookingEntity> findByDoctorIdOrderByAppointmentTimeAsc(String doctorId);

  List<BookingEntity> findByDoctorIdAndAppointmentDateOrderByAppointmentTimeAsc(
      String doctorId, LocalDate appointmentDate);

  List<BookingEntity> findByHospitalIdOrderByAppointmentTimeDesc(Long hospitalId);

  List<BookingEntity> findByPatientPhoneOrderByCreatedAtDesc(String patientPhone);

  long countByDoctorIdAndAppointmentDateAndStatusNot(String doctorId, LocalDate date, String status);
}
