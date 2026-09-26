package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * doctor_* section tables require hospital_id. Older app builds insert the contact
 * row without that column, so PostgreSQL rejects the save. A default plus a
 * before-insert trigger copies the hospital from doctor_personal when it is missing.
 */
@Component
public class DoctorHospitalIdSchemaInitializer {
  private static final String[] TABLES = {
    "doctor_personal",
    "doctor_contact",
    "doctor_professional",
    "doctor_clinic",
    "doctor_identity",
    "doctor_bank",
    "doctor_documents"
  };

  private final JdbcTemplate jdbc;

  public DoctorHospitalIdSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    jdbc.execute(
        """
        CREATE OR REPLACE FUNCTION svc.fill_doctor_section_hospital_id()
        RETURNS trigger
        LANGUAGE plpgsql
        AS $fn$
        BEGIN
          IF NEW.hospital_id IS NULL THEN
            SELECT p.hospital_id INTO NEW.hospital_id
            FROM svc.doctor_personal p
            WHERE p.doctor_id = NEW.doctor_id;
          END IF;
          IF NEW.hospital_id IS NULL THEN
            NEW.hospital_id := 10001;
          END IF;
          RETURN NEW;
        END;
        $fn$
        """);

    for (String table : TABLES) {
      if (!tableExists(table)) continue;
      jdbc.execute(
          "ALTER TABLE svc." + table + " ADD COLUMN IF NOT EXISTS hospital_id BIGINT");
      jdbc.execute("ALTER TABLE svc." + table + " ALTER COLUMN hospital_id DROP DEFAULT");
      String trigger = "trg_" + table + "_hospital_id";
      jdbc.execute("DROP TRIGGER IF EXISTS " + trigger + " ON svc." + table);
      jdbc.execute(
          "CREATE TRIGGER "
              + trigger
              + " BEFORE INSERT OR UPDATE ON svc."
              + table
              + " FOR EACH ROW EXECUTE FUNCTION svc.fill_doctor_section_hospital_id()");
    }
  }

  private boolean tableExists(String table) {
    Integer count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = 'svc' AND table_name = ?
            """,
            Integer.class,
            table);
    return count != null && count > 0;
  }
}
