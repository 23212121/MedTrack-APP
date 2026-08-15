package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ensures USER_DETAILS table + sequence exist (PostgreSQL svc schema). */
@Component
public class UserDetailsSchemaInitializer {
  private final JdbcTemplate jdbc;

  public UserDetailsSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    jdbc.execute("CREATE SEQUENCE IF NOT EXISTS svc.user_id_seq START WITH 1 INCREMENT BY 1");
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.user_details (
          user_id       VARCHAR(20)  PRIMARY KEY,
          user_name     VARCHAR(100) NOT NULL UNIQUE,
          phone         VARCHAR(32)  UNIQUE,
          password      VARCHAR(255) NOT NULL,
          created_by    VARCHAR(50),
          created_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
          updated_by    VARCHAR(50),
          updated_date  TIMESTAMPTZ
        )
        """);
    try {
      jdbc.execute("ALTER TABLE svc.user_details ADD COLUMN IF NOT EXISTS phone VARCHAR(32) UNIQUE");
    } catch (Exception ignored) {
      // column may already exist with different constraints on older DBs
    }
  }
}
