package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ambulance fleet and dispatch requests for the patient portal and driver app. */
@Component
public class AmbulanceSchemaInitializer {
  private final JdbcTemplate jdbc;

  public AmbulanceSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.ambulances (
          id                    VARCHAR(64) PRIMARY KEY,
          hospital_id           BIGINT,
          driver_name           VARCHAR(255) NOT NULL,
          mobile_number         VARCHAR(32) NOT NULL,
          latitude              DOUBLE PRECISION,
          longitude             DOUBLE PRECISION,
          fcm_token             VARCHAR(512),
          availability_status   VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
          last_location_update  TIMESTAMPTZ,
          created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
          updated_at            TIMESTAMPTZ
        )
        """);
    jdbc.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS uk_ambulance_mobile ON svc.ambulances (mobile_number)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_ambulance_status ON svc.ambulances (availability_status)");

    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.ambulance_requests (
          id                  VARCHAR(64) PRIMARY KEY,
          request_number      VARCHAR(40) NOT NULL,
          hospital_id         BIGINT,
          patient_id          VARCHAR(64),
          patient_name        VARCHAR(255),
          patient_phone       VARCHAR(32),
          patient_latitude    DOUBLE PRECISION NOT NULL,
          patient_longitude   DOUBLE PRECISION NOT NULL,
          ambulance_id        VARCHAR(64),
          status              VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
          maps_url            VARCHAR(500),
          created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
          updated_at          TIMESTAMPTZ,
          accepted_at         TIMESTAMPTZ,
          arrived_at          TIMESTAMPTZ,
          completed_at        TIMESTAMPTZ,
          cancelled_at        TIMESTAMPTZ
        )
        """);
    jdbc.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS uk_ambulance_request_number ON svc.ambulance_requests (request_number)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_ambulance_request_phone ON svc.ambulance_requests (patient_phone, created_at DESC)");

    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.ambulance_offers (
          id             VARCHAR(64) PRIMARY KEY,
          request_id     VARCHAR(64) NOT NULL,
          ambulance_id   VARCHAR(64) NOT NULL,
          status         VARCHAR(20) NOT NULL DEFAULT 'OFFERED',
          distance_km    DOUBLE PRECISION,
          offered_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
          deadline_at    TIMESTAMPTZ NOT NULL,
          responded_at   TIMESTAMPTZ
        )
        """);
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_ambulance_offer_request ON svc.ambulance_offers (request_id, status)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_ambulance_offer_driver ON svc.ambulance_offers (ambulance_id, status)");

    jdbc.execute(
        "ALTER TABLE svc.hospitals ADD COLUMN IF NOT EXISTS ambulance_rate_per_km DOUBLE PRECISION");
    jdbc.execute(
        "ALTER TABLE svc.ambulance_requests ADD COLUMN IF NOT EXISTS distance_km DOUBLE PRECISION");
    jdbc.execute(
        "ALTER TABLE svc.ambulance_requests ADD COLUMN IF NOT EXISTS rate_per_km DOUBLE PRECISION");
    jdbc.execute(
        "ALTER TABLE svc.ambulance_requests ADD COLUMN IF NOT EXISTS estimated_price DOUBLE PRECISION");
    jdbc.execute(
        "ALTER TABLE svc.ambulance_requests ADD COLUMN IF NOT EXISTS estimated_minutes INTEGER");
  }
}
