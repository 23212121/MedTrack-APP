package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ensures APPOINTMENTS table exists and drops legacy denormalized columns. */
@Component
public class AppointmentSchemaInitializer {
  private final JdbcTemplate jdbc;

  public AppointmentSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.appointments (
          appointment_id    VARCHAR(36)  PRIMARY KEY,
          hospital_id       BIGINT,
          doctor_id         VARCHAR(64)  NOT NULL,
          patient_id        VARCHAR(64)  NOT NULL,
          phone_number      VARCHAR(32),
          patient_name      VARCHAR(255) NOT NULL,
          patient_age       INTEGER,
          gender            VARCHAR(32),
          address           VARCHAR(500),
          reason            VARCHAR(1000),
          appointment_date  DATE         NOT NULL,
          appointment_time  TIMESTAMPTZ  NOT NULL,
          token_number      INTEGER,
          status            VARCHAR(32)  NOT NULL DEFAULT 'BOOKED',
          booked_by         VARCHAR(32),
          booking_ref_id    VARCHAR(36),
          created_by        VARCHAR(50),
          created_date      TIMESTAMPTZ  NOT NULL DEFAULT now(),
          updated_by        VARCHAR(50),
          updated_date      TIMESTAMPTZ
        )
        """);
    dropLegacyAppointmentColumns();
    dropLegacyBookingColumns();
    widenPatientIdColumn();
    ensurePhoneNumberColumn();
    ensureDepartmentColumn();
    ensureAppointmentIndexes();
  }

  /** Lookup indexes: doctor_id, hospital_id, phone_number, patient_id. */
  private void ensureAppointmentIndexes() {
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_appointments_doctor ON svc.appointments(doctor_id)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_appointments_hospital ON svc.appointments(hospital_id)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_appointments_phone ON svc.appointments(phone_number)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_appointments_patient ON svc.appointments(patient_id)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_appointments_doctor_date ON svc.appointments(doctor_id, appointment_date)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_appointments_hospital_doctor ON svc.appointments(hospital_id, doctor_id)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_appointments_department ON svc.appointments(department)");
  }

  private void ensureDepartmentColumn() {
    jdbc.execute(
        "ALTER TABLE svc.appointments ADD COLUMN IF NOT EXISTS department VARCHAR(255)");
  }

  private void ensurePhoneNumberColumn() {
    jdbc.execute(
        "ALTER TABLE svc.appointments ADD COLUMN IF NOT EXISTS phone_number VARCHAR(32)");
  }

  private void dropLegacyAppointmentColumns() {
    for (String col :
        List.of(
            "clinic_id",
            "clinic_name",
            "doctor_name",
            "consultation_fee",
            "currency")) {
      jdbc.execute(
          "ALTER TABLE svc.appointments DROP COLUMN IF EXISTS " + col);
    }
  }

  private void dropLegacyBookingColumns() {
    for (String col :
        List.of(
            "clinic_id",
            "clinic_name",
            "doctor_name",
            "consultation_fee",
            "currency")) {
      jdbc.execute("ALTER TABLE svc.bookings DROP COLUMN IF EXISTS " + col);
    }
  }

  private void widenPatientIdColumn() {
    jdbc.execute("ALTER TABLE svc.appointments ALTER COLUMN patient_id TYPE VARCHAR(64)");
  }
}
