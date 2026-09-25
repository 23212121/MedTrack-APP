package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@DependsOn("paymentSchemaInitializer")
public class EmergencySchemaInitializer {
  private final JdbcTemplate jdbc;

  public EmergencySchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.emergency_beds (
          id           VARCHAR(64) PRIMARY KEY,
          hospital_id  BIGINT NOT NULL,
          bed_number   VARCHAR(20) NOT NULL,
          ward         VARCHAR(40) NOT NULL DEFAULT 'EMERGENCY',
          fees         DOUBLE PRECISION NOT NULL DEFAULT 2500,
          status       VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
          notes        VARCHAR(500),
          created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
        )
        """);
    jdbc.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS uk_emergency_bed_hospital_number ON svc.emergency_beds (hospital_id, lower(bed_number))");
    jdbc.execute("CREATE INDEX IF NOT EXISTS idx_emergency_beds_hospital ON svc.emergency_beds (hospital_id, status)");

    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.emergency_bed_bookings (
          id                  VARCHAR(64) PRIMARY KEY,
          hospital_id         BIGINT NOT NULL,
          bed_id              VARCHAR(64) NOT NULL,
          bed_number          VARCHAR(20),
          patient_name        VARCHAR(255) NOT NULL,
          patient_phone       VARCHAR(32),
          patient_id          VARCHAR(64),
          booked_by_login_id  VARCHAR(100),
          booked_by_type      VARCHAR(20),
          status              VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
          fees                DOUBLE PRECISION,
          payment_id          VARCHAR(40),
          payment_status      VARCHAR(30) DEFAULT 'UNPAID',
          payment_slip_path   VARCHAR(1000),
          payment_slip_name   VARCHAR(255),
          notes               TEXT,
          created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
          updated_at          TIMESTAMPTZ
        )
        """);
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_emergency_bookings_hospital ON svc.emergency_bed_bookings (hospital_id, created_at DESC)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_emergency_bookings_bed ON svc.emergency_bed_bookings (bed_id, status)");

    jdbc.execute("ALTER TABLE svc.payments ALTER COLUMN appointment_id DROP NOT NULL");
    jdbc.execute(
        "ALTER TABLE svc.payments ADD COLUMN IF NOT EXISTS reference_type VARCHAR(30) DEFAULT 'APPOINTMENT'");
    jdbc.execute("ALTER TABLE svc.payments ADD COLUMN IF NOT EXISTS reference_id VARCHAR(64)");
    jdbc.execute(
        "UPDATE svc.payments SET reference_type = 'APPOINTMENT' WHERE reference_type IS NULL OR btrim(reference_type) = ''");
    jdbc.execute(
        "UPDATE svc.payments SET reference_id = appointment_id WHERE (reference_id IS NULL OR btrim(reference_id) = '') AND appointment_id IS NOT NULL");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_payments_reference ON svc.payments (reference_type, reference_id)");
  }
}
