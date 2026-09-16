-- Hospital HRM posts shown on Home → Announcements
CREATE TABLE IF NOT EXISTS svc.hrm_posts (
  id              VARCHAR(36) PRIMARY KEY,
  hospital_id     BIGINT NOT NULL,
  doctor_id       VARCHAR(64) NOT NULL,
  title           VARCHAR(240) NOT NULL,
  body            TEXT NOT NULL,
  likes           INTEGER NOT NULL DEFAULT 0,
  comments        INTEGER NOT NULL DEFAULT 0,
  creation_date   TIMESTAMPTZ NOT NULL DEFAULT now(),
  creation_user   VARCHAR(100) NOT NULL,
  update_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
  update_user     VARCHAR(100) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_hrm_posts_hospital_doctor
  ON svc.hrm_posts (hospital_id, doctor_id, creation_date DESC);
