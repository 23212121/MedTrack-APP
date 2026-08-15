package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ensures svc.login exists and seeds hospital test logins. */
@Component
public class LoginSchemaInitializer {
  private final JdbcTemplate jdbc;

  public LoginSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
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
          status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
          creation_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
          creation_user  VARCHAR(100),
          update_date    TIMESTAMPTZ,
          update_user    VARCHAR(100)
        )
        """);
    jdbc.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS uk_login_type_id ON svc.login (login_type, lower(login_id))");
    jdbc.execute("CREATE INDEX IF NOT EXISTS idx_login_hospital ON svc.login (hospital_id)");

    seedHospitalLogins();
    syncHospitalAdminPasswords();
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
          ) VALUES (?, 'HOSPITAL', ?, ?, ?, ?, 'ACTIVE', now(), 'seed')
          ON CONFLICT (id) DO UPDATE SET
            password = EXCLUDED.password,
            display_name = EXCLUDED.display_name,
            status = 'ACTIVE',
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
          'ACTIVE',
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
          status = 'ACTIVE',
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
          AND l.status = 'ACTIVE'
        """);
  }
}
