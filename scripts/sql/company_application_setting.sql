-- Hospital HRM tab rights. Grant a right_code to a user to show that HRM tab.
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
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_company_app_setting_user_right
  ON svc.company_application_setting (hospital_id, user_id, right_code);

CREATE INDEX IF NOT EXISTS idx_company_app_setting_hospital_user
  ON svc.company_application_setting (hospital_id, user_id);
