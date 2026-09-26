package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ensures svc.status lookup and svc.login exist, and migrates login.status to 0/1. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoginSchemaInitializer {
  private final JdbcTemplate jdbc;

  public LoginSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    ensureStatusLookup();
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.login (
          id             VARCHAR(64)  PRIMARY KEY,
          login_type     VARCHAR(20)  NOT NULL,
          login_id       VARCHAR(100) NOT NULL,
          password       VARCHAR(255) NOT NULL,
          hospital_id    BIGINT,
          doctor_id      VARCHAR(64),
          display_name   VARCHAR(255),
          status         SMALLINT     NOT NULL DEFAULT 1,
          creation_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
          creation_user  VARCHAR(100),
          update_date    TIMESTAMPTZ,
          update_user    VARCHAR(100)
        )
        """);
    jdbc.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS uk_login_type_id ON svc.login (login_type, lower(login_id))");
    jdbc.execute("CREATE INDEX IF NOT EXISTS idx_login_hospital ON svc.login (hospital_id)");
    migrateLoginStatusToDigits();
    jdbc.execute("ALTER TABLE svc.login DROP CONSTRAINT IF EXISTS fk_login_status");
    jdbc.execute(
        """
        ALTER TABLE svc.login
          ADD CONSTRAINT fk_login_status
          FOREIGN KEY (status) REFERENCES svc.status(status_id)
        """);
  }

  private void ensureStatusLookup() {
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.status (
          status_id   SMALLINT PRIMARY KEY,
          status_code VARCHAR(20) NOT NULL,
          status_name VARCHAR(40) NOT NULL
        )
        """);
    jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_status_code ON svc.status (status_code)");
  }

  /** Convert existing VARCHAR ACTIVE/INACTIVE values to SMALLINT 0/1. */
  private void migrateLoginStatusToDigits() {
    Integer varcharCols =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = 'svc' AND table_name = 'login' AND column_name = 'status'
              AND data_type IN ('character varying', 'character', 'text')
            """,
            Integer.class);
    if (varcharCols == null || varcharCols == 0) {
      return;
    }
    jdbc.execute("ALTER TABLE svc.login ALTER COLUMN status DROP DEFAULT");
    jdbc.execute(
        """
        ALTER TABLE svc.login
          ALTER COLUMN status TYPE SMALLINT
          USING CASE
            WHEN UPPER(TRIM(status::text)) IN ('INACTIVE', '0', 'FALSE', 'F', 'NO') THEN 0
            WHEN UPPER(TRIM(status::text)) IN ('ACTIVE', '1', 'TRUE', 'T', 'YES') THEN 1
            WHEN TRIM(status::text) ~ '^[0-9]+$' THEN LEAST(1, GREATEST(0, TRIM(status::text)::SMALLINT))
            ELSE 1
          END
        """);
    jdbc.execute("ALTER TABLE svc.login ALTER COLUMN status SET DEFAULT 1");
    jdbc.execute("ALTER TABLE svc.login ALTER COLUMN status SET NOT NULL");
  }
}
