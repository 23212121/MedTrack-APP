-- Leave applications: user/doctor can apply leave.
-- Mandatory: doctor_id, hospital_id, creation_date, creation_user, update_date, update_user

CREATE TABLE IF NOT EXISTS svc.leave_applications (
  id                 VARCHAR(64)  PRIMARY KEY,
  hospital_id        BIGINT       NOT NULL,
  doctor_id          VARCHAR(64)  NOT NULL,
  employee_id        VARCHAR(40)  NOT NULL,
  employee_name      VARCHAR(160),
  leave_type_id      VARCHAR(64)  NOT NULL,
  leave_type         VARCHAR(80)  NOT NULL,
  from_date          DATE         NOT NULL,
  to_date            DATE         NOT NULL,
  days               NUMERIC(6,2) NOT NULL DEFAULT 1,
  reason             VARCHAR(500),
  status             VARCHAR(20)  NOT NULL DEFAULT 'Pending',
  approver_remarks   VARCHAR(400),
  creation_date      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creation_user      VARCHAR(100) NOT NULL,
  update_date        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  update_user        VARCHAR(100) NOT NULL,
  CONSTRAINT ck_leave_app_dates CHECK (to_date >= from_date),
  CONSTRAINT ck_leave_app_status CHECK (status IN ('Pending', 'Approved', 'Rejected', 'Cancelled')),
  CONSTRAINT fk_leave_app_hospital
    FOREIGN KEY (hospital_id) REFERENCES svc.hospitals (id)
);

CREATE INDEX IF NOT EXISTS idx_leave_app_hospital_doctor
  ON svc.leave_applications (hospital_id, doctor_id);

CREATE INDEX IF NOT EXISTS idx_leave_app_doctor_status
  ON svc.leave_applications (doctor_id, status);

CREATE INDEX IF NOT EXISTS idx_leave_app_from_date
  ON svc.leave_applications (from_date);

COMMENT ON TABLE svc.leave_applications IS
  'Leave requests applied by a doctor/user; hospital + doctor + audit columns are mandatory.';
