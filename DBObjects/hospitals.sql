-- Hospital master table. Unique id starts at 10001.
-- Schema: svc (MedTrack booking-service / PostgreSQL)

CREATE TABLE IF NOT EXISTS svc.hospitals (
  id                   BIGINT       PRIMARY KEY,
  hospital_code        VARCHAR(40)  NOT NULL,
  hospital_name        VARCHAR(255) NOT NULL,
  hospital_type        VARCHAR(100),
  registration_number  VARCHAR(120),
  email                VARCHAR(255),
  primary_contact      VARCHAR(60),
  city                 VARCHAR(120),
  state                VARCHAR(120),
  country              VARCHAR(120),
  subscription_plan    VARCHAR(60),
  status               VARCHAR(40)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
  registration_json    TEXT,
  created_at           TIMESTAMPTZ  DEFAULT NOW(),
  updated_at           TIMESTAMPTZ  DEFAULT NOW(),
  CONSTRAINT uk_hospitals_code UNIQUE (hospital_code)
);

COMMENT ON TABLE svc.hospitals IS 'Registered hospitals; id is unique and starts from 10001';
COMMENT ON COLUMN svc.hospitals.id IS 'Auto-assigned unique hospital ID (10001, 10002, ...)';

-- Optional sequence helper (app also assigns id in service via max(id)+1 from 10001)
CREATE SEQUENCE IF NOT EXISTS svc.hospitals_id_seq START WITH 10001 INCREMENT BY 1;
