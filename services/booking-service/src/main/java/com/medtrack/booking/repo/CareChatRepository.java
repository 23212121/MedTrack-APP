package com.medtrack.booking.repo;

import com.medtrack.booking.domain.CareChatEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareChatRepository extends JpaRepository<CareChatEntity, String> {
  List<CareChatEntity> findByAppointmentIdOrderByCreatedDateAsc(String appointmentId);

  List<CareChatEntity> findByAppointmentIdIn(Collection<String> appointmentIds);

  long countByAppointmentId(String appointmentId);

  List<CareChatEntity> findByPatientPhoneOrderByCreatedDateDesc(String patientPhone);
}
