package com.medtrack.hrm.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CompanyApplicationSettingSchemaInitializer {
  private static final Logger log =
      LoggerFactory.getLogger(CompanyApplicationSettingSchemaInitializer.class);
  private final JdbcTemplate jdbc;

  public CompanyApplicationSettingSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.company_application_setting (
          id              VARCHAR(36) PRIMARY KEY,
          hospital_id     BIGINT NOT NULL,
          user_id         VARCHAR(64) NOT NULL,
          right_code      VARCHAR(80) NOT NULL,
          allowed         BOOLEAN NOT NULL DEFAULT TRUE,
          creation_date   TIMESTAMPTZ NOT NULL DEFAULT now(),
          creation_user   VARCHAR(100) NOT NULL,
          update_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
          update_user     VARCHAR(100) NOT NULL
        )
        """);
    jdbc.execute(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS uk_company_app_setting_user_right
          ON svc.company_application_setting (hospital_id, user_id, right_code)
        """);
    jdbc.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_company_app_setting_hospital_user
          ON svc.company_application_setting (hospital_id, user_id)
        """);
    log.info("company_application_setting table is ready");
  }
}
