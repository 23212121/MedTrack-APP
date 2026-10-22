package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Lab test bookings: home collection / in-lab visit, notifications, and booking chat. */
@Component
@DependsOn("labSchemaInitializer")
public class LabBookingSchemaInitializer {
  private final JdbcTemplate jdbc;

  public LabBookingSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.lab_bookings (
          id                  VARCHAR(64) PRIMARY KEY,
          booking_number      VARCHAR(40) NOT NULL,
          hospital_id         BIGINT NOT NULL,
          patient_id          VARCHAR(64),
          patient_name        VARCHAR(255) NOT NULL,
          patient_phone       VARCHAR(32) NOT NULL,
          mode                VARCHAR(30) NOT NULL,
          collection_address  VARCHAR(500),
          notes               VARCHAR(1000),
          preferred_at        TIMESTAMPTZ,
          status              VARCHAR(40) NOT NULL DEFAULT 'PENDING',
          assigned_lab_id     VARCHAR(64),
          assigned_lab_name   VARCHAR(255),
          confirmed_at        TIMESTAMPTZ,
          visit_time          TIMESTAMPTZ,
          token_number        INTEGER,
          expected_visit_at   TIMESTAMPTZ,
          queue_date          DATE,
          tech_reached_at     TIMESTAMPTZ,
          cancel_reason       VARCHAR(500),
          cancel_charge       DOUBLE PRECISION,
          distance_km         DOUBLE PRECISION,
          cancelled_by        VARCHAR(100),
          cancelled_at        TIMESTAMPTZ,
          completed_at        TIMESTAMPTZ,
          completed_by        VARCHAR(100),
          created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
          created_by          VARCHAR(100),
          updated_at          TIMESTAMPTZ,
          updated_by          VARCHAR(100)
        )
        """);
    jdbc.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS uk_lab_booking_number ON svc.lab_bookings (booking_number)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_lab_booking_hospital ON svc.lab_bookings (hospital_id, status)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_lab_booking_phone ON svc.lab_bookings (patient_phone)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_lab_booking_lab ON svc.lab_bookings (assigned_lab_id, status)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_lab_booking_queue ON svc.lab_bookings (hospital_id, assigned_lab_id, queue_date)");

    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.lab_booking_notifications (
          id             VARCHAR(64) PRIMARY KEY,
          hospital_id    BIGINT,
          lab_id         VARCHAR(64),
          patient_phone  VARCHAR(32),
          audience       VARCHAR(20) NOT NULL,
          booking_id     VARCHAR(64) NOT NULL,
          title          VARCHAR(255) NOT NULL,
          message        VARCHAR(1000) NOT NULL,
          read_flag      BOOLEAN NOT NULL DEFAULT false,
          created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
        )
        """);
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_lab_booking_notif_lab ON svc.lab_booking_notifications (lab_id, created_at DESC)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_lab_booking_notif_phone ON svc.lab_booking_notifications (patient_phone, created_at DESC)");

    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.lab_booking_chats (
          id             VARCHAR(64) PRIMARY KEY,
          booking_id     VARCHAR(64) NOT NULL,
          hospital_id    BIGINT NOT NULL,
          sender_type    VARCHAR(20) NOT NULL,
          sender_name    VARCHAR(255),
          message_text   VARCHAR(2000) NOT NULL,
          created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
          created_by     VARCHAR(100)
        )
        """);
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_lab_booking_chat ON svc.lab_booking_chats (booking_id, created_at)");

    jdbc.execute(
        "ALTER TABLE svc.lab_bookings ADD COLUMN IF NOT EXISTS patient_latitude DOUBLE PRECISION");
    jdbc.execute(
        "ALTER TABLE svc.lab_bookings ADD COLUMN IF NOT EXISTS patient_longitude DOUBLE PRECISION");
    jdbc.execute(
        "ALTER TABLE svc.lab_bookings ADD COLUMN IF NOT EXISTS trip_distance_km DOUBLE PRECISION");
    jdbc.execute(
        "ALTER TABLE svc.lab_bookings ADD COLUMN IF NOT EXISTS trip_charge DOUBLE PRECISION");
  }
}
