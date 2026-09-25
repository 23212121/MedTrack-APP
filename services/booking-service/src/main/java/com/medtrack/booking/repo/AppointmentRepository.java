package com.medtrack.booking.repo;

import com.medtrack.booking.domain.AppointmentEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, String> {
  Optional<AppointmentEntity> findByBookingRefId(String bookingRefId);

  List<AppointmentEntity> findByPatientIdOrderByCreatedDateDesc(String patientId);

  List<AppointmentEntity> findByPhoneNumberOrderByCreatedDateDesc(String phoneNumber);

  List<AppointmentEntity> findByDoctorIdOrderByAppointmentTimeAsc(String doctorId);

  List<AppointmentEntity> findByDoctorIdAndAppointmentDateOrderByAppointmentTimeAsc(
      String doctorId, LocalDate appointmentDate);

  List<AppointmentEntity> findByHospitalIdOrderByCreatedDateDesc(Long hospitalId);

  List<AppointmentEntity> findFirst20ByPatientNameContainingIgnoreCaseOrderByCreatedDateDesc(
      String patientName);

  List<AppointmentEntity>
      findByHospitalIdAndDoctorIdAndAppointmentDateAndStatusNotOrderByTokenNumberAscAppointmentTimeAsc(
          Long hospitalId, String doctorId, LocalDate appointmentDate, String status);

  long countByDoctorIdAndAppointmentDateAndStatusNot(
      String doctorId, LocalDate date, String status);

  long countByHospitalIdAndDoctorIdAndAppointmentDateAndStatusNot(
      Long hospitalId, String doctorId, LocalDate date, String status);

  long countByHospitalIdAndDoctorIdAndAppointmentDateAndStatus(
      Long hospitalId, String doctorId, LocalDate date, String status);
}
