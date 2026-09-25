package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ensures svc.status lookup + svc.login exist, migrates login.status to 0/1, and seeds test logins. */
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

    seedHospitalLogins();
    syncHospitalAdminPasswords();
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
    jdbc.update(
        """
        INSERT INTO svc.status (status_id, status_code, status_name) VALUES
          (0, 'INACTIVE', 'Inactive'),
          (1, 'ACTIVE', 'Active')
        ON CONFLICT (status_id) DO UPDATE SET
          status_code = EXCLUDED.status_code,
          status_name = EXCLUDED.status_name
        """);
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

  private void seedHospitalLogins() {
    // Demo password for all seeded hospital + doctor accounts
    final String demoPassword = "123456";
    Object[][] hospitals = {
      {"login-hosp-10001", "10001", 10001L, "Test Hospital"},
      {"login-hosp-10002", "10002", 10002L, "Sunrise Care Hospital"},
      {"login-hosp-10003", "10003", 10003L, "City Heart Institute"},
      {"login-hosp-10005", "10005", 10005L, "Apollo Metro Hospital"},
      {"login-hosp-10009", "10009", 10009L, "Ocean View Medical"},
    };
    for (Object[] r : hospitals) {
      jdbc.update(
          """
          INSERT INTO svc.login (
            id, login_type, login_id, password, hospital_id, display_name,
            status, creation_date, creation_user
          ) VALUES (?, 'HOSPITAL', ?, ?, ?, ?, 1, now(), 'seed')
          ON CONFLICT (id) DO UPDATE SET
            password = EXCLUDED.password,
            display_name = EXCLUDED.display_name,
            status = 1,
            update_date = now(),
            update_user = 'seed'
          """,
          r[0],
          r[1],
          demoPassword,
          r[2],
          r[3]);
    }

    // Seed doctor USER logins from doctor_personal + doctor_clinic
    jdbc.update(
        """
        INSERT INTO svc.login (
          id, login_type, login_id, password, hospital_id, doctor_id, display_name,
          status, creation_date, creation_user
        )
        SELECT
          'login-user-' || dp.doctor_id,
          'USER',
          dp.doctor_id,
          ?,
          dc.hospital_id,
          dp.doctor_id,
          trim(both ' ' from coalesce(dp.first_name, '') || ' ' || coalesce(dp.last_name, '')),
          1,
          now(),
          'seed'
        FROM svc.doctor_personal dp
        LEFT JOIN svc.doctor_clinic dc ON dc.doctor_id = dp.doctor_id
        WHERE dp.doctor_id LIKE 'DOC-SEED-%'
        ON CONFLICT (id) DO UPDATE SET
          password = EXCLUDED.password,
          hospital_id = EXCLUDED.hospital_id,
          doctor_id = EXCLUDED.doctor_id,
          display_name = EXCLUDED.display_name,
          status = 1,
          update_date = now(),
          update_user = 'seed'
        """,
        demoPassword);

    jdbc.update(
        """
        UPDATE svc.doctor_personal
        SET login_password = ?, updated_date = now()
        WHERE doctor_id LIKE 'DOC-SEED-%'
        """,
        demoPassword);
  }

  /** Keep hospitals.admin_password in sync for legacy AuthService fallback. */
  private void syncHospitalAdminPasswords() {
    jdbc.update(
        """
        UPDATE svc.hospitals h
        SET admin_password = l.password,
            admin_email = COALESCE(NULLIF(h.admin_email, ''), 'admin@hospital-' || h.id || '.local'),
            updated_at = now()
        FROM svc.login l
        WHERE l.login_type = 'HOSPITAL'
          AND l.hospital_id = h.id
          AND l.status = 1
        """);
  }
}
