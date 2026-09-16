package com.medtrack.hrm.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class HrmHolidaySchemaInitializer {
  private static final Logger log = LoggerFactory.getLogger(HrmHolidaySchemaInitializer.class);
  private final JdbcTemplate jdbc;

  public HrmHolidaySchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.hrm_holidays (
          id              VARCHAR(255) PRIMARY KEY,
          hospital_id     BIGINT,
          doctor_id       VARCHAR(64) NOT NULL,
          holiday_date    DATE NOT NULL,
          name            VARCHAR(120) NOT NULL,
          description     VARCHAR(400),
          reason          VARCHAR(400),
          creation_date   TIMESTAMPTZ NOT NULL DEFAULT now(),
          creation_user   VARCHAR(100) NOT NULL,
          update_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
          update_user     VARCHAR(100) NOT NULL
        )
        """);
    jdbc.execute("ALTER TABLE svc.hrm_holidays ADD COLUMN IF NOT EXISTS hospital_id BIGINT");
    jdbc.execute("ALTER TABLE svc.hrm_holidays ADD COLUMN IF NOT EXISTS reason VARCHAR(400)");
    jdbc.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_hrm_holidays_hospital_date
          ON svc.hrm_holidays (hospital_id, holiday_date)
        """);
    log.info("hrm_holidays table is ready");
  }
}
