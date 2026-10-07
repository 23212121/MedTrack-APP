package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Public /track OTP gate. {@code flag = Y} sends an SMS OTP before showing appointments;
 * {@code flag = N} skips OTP.
 */
@Component
public class TrackOtpSchemaInitializer {
  private final JdbcTemplate jdbc;

  public TrackOtpSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.track_otp_config (
          id          VARCHAR(32) PRIMARY KEY,
          flag        VARCHAR(10) NOT NULL DEFAULT 'Y',
          updated_at  TIMESTAMPTZ
        )
        """);
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.track_otp (
          id          VARCHAR(64) PRIMARY KEY,
          phone       VARCHAR(32) NOT NULL,
          otp_hash    VARCHAR(128) NOT NULL,
          expires_at  TIMESTAMPTZ NOT NULL,
          verified    BOOLEAN NOT NULL DEFAULT FALSE,
          created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
        )
        """);
    jdbc.execute("CREATE INDEX IF NOT EXISTS idx_track_otp_phone ON svc.track_otp (phone, created_at DESC)");
    jdbc.update(
        """
        INSERT INTO svc.track_otp_config (id, flag, updated_at)
        VALUES ('DEFAULT', 'Y', now())
        ON CONFLICT (id) DO NOTHING
        """);
  }
}
