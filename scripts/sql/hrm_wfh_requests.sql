-- Work From Home requests (Attendance → Work From Home popup)
CREATE TABLE IF NOT EXISTS svc.hrm_wfh_requests (
  id              VARCHAR(64)  PRIMARY KEY,
  hospital_id     BIGINT       NOT NULL,
  doctor_id       VARCHAR(64)  NOT NULL,
  employee_id     VARCHAR(40)  NOT NULL,
  employee_name   VARCHAR(160),
  from_date       DATE         NOT NULL,
  to_date         DATE         NOT NULL,
  days            NUMERIC(6,2) NOT NULL DEFAULT 1,
  note            VARCHAR(1000),
  notify_to       VARCHAR(500),
  status          VARCHAR(20)  NOT NULL DEFAULT 'Pending',
  creation_date   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creation_user   VARCHAR(100) NOT NULL,
  update_date     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  update_user     VARCHAR(100) NOT NULL,
  CONSTRAINT ck_hrm_wfh_dates CHECK (to_date >= from_date),
  CONSTRAINT ck_hrm_wfh_status CHECK (status IN ('Pending', 'Approved', 'Rejected', 'Cancelled')),
  CONSTRAINT fk_hrm_wfh_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals (id)
);

CREATE INDEX IF NOT EXISTS idx_hrm_wfh_hospital_doctor
  ON svc.hrm_wfh_requests (hospital_id, doctor_id);

CREATE INDEX IF NOT EXISTS idx_hrm_wfh_from_date
  ON svc.hrm_wfh_requests (from_date);
