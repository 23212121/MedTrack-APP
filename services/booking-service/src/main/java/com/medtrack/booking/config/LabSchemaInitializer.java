package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Diagnostic lab technicians + demo LAB-10001-1 login. */
@Component
@DependsOn("loginSchemaInitializer")
public class LabSchemaInitializer {
  private final JdbcTemplate jdbc;

  public LabSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.lab_technicians (
          id             VARCHAR(64) PRIMARY KEY,
          hospital_id    BIGINT,
          lab_code       VARCHAR(40) NOT NULL,
          lab_name       VARCHAR(255) NOT NULL,
          phone          VARCHAR(32),
          address        VARCHAR(500),
          city           VARCHAR(120),
          state          VARCHAR(120),
          status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
          created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
          created_by     VARCHAR(100),
          updated_at     TIMESTAMPTZ,
          updated_by     VARCHAR(100)
        )
        """);
    jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_lab_code ON svc.lab_technicians (lab_code)");
    jdbc.execute("CREATE INDEX IF NOT EXISTS idx_lab_hospital ON svc.lab_technicians (hospital_id)");

    try {
      jdbc.execute(
          "ALTER TABLE svc.patients ADD COLUMN IF NOT EXISTS aadhaar_number VARCHAR(20)");
    } catch (Exception ignored) {
      // patients table may not exist yet on first boot
    }
    try {
      jdbc.execute("CREATE INDEX IF NOT EXISTS idx_patients_aadhaar ON svc.patients (aadhaar_number)");
    } catch (Exception ignored) {
      // ignore
    }
    try {
      jdbc.execute("ALTER TABLE svc.documents ADD COLUMN IF NOT EXISTS lab_id VARCHAR(64)");
    } catch (Exception ignored) {
      // ignore
    }
    try {
      jdbc.execute("CREATE INDEX IF NOT EXISTS idx_documents_lab ON svc.documents (lab_id)");
    } catch (Exception ignored) {
      // ignore
    }

    seedDemoLab();
  }

  private void seedDemoLab() {
    Integer labs =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM svc.lab_technicians WHERE lower(lab_code) = lower('LAB-10001-1')",
            Integer.class);
    if (labs != null && labs > 0) {
      return;
    }
    jdbc.execute(
        """
        INSERT INTO svc.lab_technicians (
          id, hospital_id, lab_code, lab_name, phone, city, state, status, created_by
        ) VALUES (
          'lab-10001-1', 10001, 'LAB-10001-1', 'MedTrack Diagnostic Lab',
          '9000000001', 'Hyderabad', 'Telangana', 'ACTIVE', 'seed'
        )
        """);
    Integer logins =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM svc.login
            WHERE login_type = 'LAB' AND lower(login_id) = lower('LAB-10001-1')
            """,
            Integer.class);
    if (logins != null && logins > 0) {
      return;
    }
    jdbc.execute(
        """
        INSERT INTO svc.login (
          id, login_type, login_id, password, hospital_id, display_name, status, creation_user
        ) VALUES (
          'login-lab-10001-1', 'LAB', 'LAB-10001-1', '123456', 10001,
          'MedTrack Diagnostic Lab', 1, 'seed'
        )
        """);
  }
}
