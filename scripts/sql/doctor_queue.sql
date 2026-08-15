-- Patient Queue & Waiting Time (schema: svc)
CREATE SCHEMA IF NOT EXISTS svc;

CREATE TABLE IF NOT EXISTS svc.doctor_queue (
  id                   BIGSERIAL PRIMARY KEY,
  hospital_id          BIGINT       NOT NULL,
  doctor_id            VARCHAR(64)  NOT NULL,
  token_no             INTEGER      NOT NULL,
  patient_id           VARCHAR(64),
  patient_name         VARCHAR(200),
  patient_phone        VARCHAR(40),
  visit_id             VARCHAR(64),
  status               VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
  checkin_time         TIMESTAMPTZ,
  consultation_start   TIMESTAMPTZ,
  consultation_end     TIMESTAMPTZ,
  queue_date           DATE         NOT NULL,
  sms_two_ahead_sent   BOOLEAN      NOT NULL DEFAULT FALSE,
  doctor_name          VARCHAR(200),
  department           VARCHAR(120),
  CONSTRAINT uq_doctor_queue_day_token UNIQUE (hospital_id, doctor_id, queue_date, token_no)
);

CREATE INDEX IF NOT EXISTS idx_doctor_queue_lookup
  ON svc.doctor_queue (hospital_id, doctor_id, queue_date, status);

GRANT ALL ON TABLE svc.doctor_queue TO medtrack;
GRANT USAGE, SELECT ON SEQUENCE svc.doctor_queue_id_seq TO medtrack;
