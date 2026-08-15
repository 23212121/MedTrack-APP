-- Table name: user (doctor_id FK → doctor_details.doctor_id)
-- Quoted because USER is a reserved word in PostgreSQL.
CREATE SCHEMA IF NOT EXISTS svc;

CREATE TABLE IF NOT EXISTS svc."user" (
  id              VARCHAR(36) PRIMARY KEY,
  name            VARCHAR(255) NOT NULL,
  value           VARCHAR(255) NOT NULL,
  description     TEXT,
  user_creation   VARCHAR(120),
  update_date     TIMESTAMPTZ,
  update_user     VARCHAR(120),
  creation_date   TIMESTAMPTZ NOT NULL,
  flag            VARCHAR(10) NOT NULL DEFAULT 'Y',
  hospital_id     BIGINT,
  doctor_id       VARCHAR(40) NOT NULL,
  CONSTRAINT fk_user_doctor
    FOREIGN KEY (doctor_id) REFERENCES svc.doctor_details (doctor_id)
);

CREATE INDEX IF NOT EXISTS idx_user_doctor
  ON svc."user" (doctor_id);

CREATE INDEX IF NOT EXISTS idx_user_hospital
  ON svc."user" (hospital_id);
