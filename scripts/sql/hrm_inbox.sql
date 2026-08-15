-- HRM Inbox: performance reviews, letter releases, salary increment letters
-- Mandatory: doctor_id, hospital_id, creation_date, creation_user, update_date, update_user

CREATE TABLE IF NOT EXISTS svc.hrm_inbox (
  id                 VARCHAR(64)  PRIMARY KEY,
  hospital_id        BIGINT       NOT NULL,
  doctor_id          VARCHAR(64)  NOT NULL,
  employee_id        VARCHAR(40)  NOT NULL,
  employee_name      VARCHAR(160),
  category           VARCHAR(40)  NOT NULL,
  document_name      VARCHAR(200) NOT NULL,
  title              VARCHAR(240) NOT NULL,
  status             VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
  action_required    VARCHAR(80)  NOT NULL DEFAULT 'Acknowledgement Required',
  message_body       VARCHAR(1000),
  file_url           VARCHAR(500),
  requested_by       VARCHAR(120),
  requested_at       TIMESTAMPTZ,
  acknowledged_at    TIMESTAMPTZ,
  archived           BOOLEAN      NOT NULL DEFAULT FALSE,
  creation_date      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creation_user      VARCHAR(100) NOT NULL,
  update_date        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  update_user        VARCHAR(100) NOT NULL,
  CONSTRAINT ck_hrm_inbox_category CHECK (
    category IN ('PERFORMANCE', 'LETTER_RELEASE', 'SALARY_INCREMENT', 'DOCUMENTS')
  ),
  CONSTRAINT ck_hrm_inbox_status CHECK (
    status IN ('PENDING', 'ACKNOWLEDGED', 'DOWNLOADED', 'ARCHIVED')
  ),
  CONSTRAINT fk_hrm_inbox_hospital
    FOREIGN KEY (hospital_id) REFERENCES svc.hospitals (id)
);

CREATE INDEX IF NOT EXISTS idx_hrm_inbox_hospital_doctor
  ON svc.hrm_inbox (hospital_id, doctor_id);

CREATE INDEX IF NOT EXISTS idx_hrm_inbox_status
  ON svc.hrm_inbox (doctor_id, status, archived);

COMMENT ON TABLE svc.hrm_inbox IS
  'Inbox tasks for performance / letter release / salary increment documents requiring download or acknowledgement.';
