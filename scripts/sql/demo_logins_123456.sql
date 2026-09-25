-- Demo logins: password for ALL test accounts = 123456
CREATE SCHEMA IF NOT EXISTS svc;

CREATE TABLE IF NOT EXISTS svc.status (
  status_id   SMALLINT PRIMARY KEY,
  status_code VARCHAR(20) NOT NULL,
  status_name VARCHAR(40) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_status_code ON svc.status (status_code);
INSERT INTO svc.status (status_id, status_code, status_name) VALUES
  (0, 'INACTIVE', 'Inactive'),
  (1, 'ACTIVE', 'Active')
ON CONFLICT (status_id) DO UPDATE SET
  status_code = EXCLUDED.status_code,
  status_name = EXCLUDED.status_name;

CREATE TABLE IF NOT EXISTS svc.login (
  id             VARCHAR(64)  PRIMARY KEY,
  login_type     VARCHAR(20)  NOT NULL,
  login_id       VARCHAR(100) NOT NULL,
  password       VARCHAR(255) NOT NULL,
  hospital_id    BIGINT,
  doctor_id      VARCHAR(64),
  display_name   VARCHAR(255),
  status         SMALLINT     NOT NULL DEFAULT 1,
  creation_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  creation_user  VARCHAR(100),
  update_date    TIMESTAMPTZ,
  update_user    VARCHAR(100)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_login_type_id
  ON svc.login (login_type, lower(login_id));

-- Hospitals: password 123456
INSERT INTO svc.login (
  id, login_type, login_id, password, hospital_id, display_name,
  status, creation_date, creation_user
) VALUES
  ('login-hosp-10001', 'HOSPITAL', '10001', '123456', 10001, 'Test Hospital',         1, now(), 'seed'),
  ('login-hosp-10002', 'HOSPITAL', '10002', '123456', 10002, 'Sunrise Care Hospital', 1, now(), 'seed'),
  ('login-hosp-10003', 'HOSPITAL', '10003', '123456', 10003, 'City Heart Institute',  1, now(), 'seed'),
  ('login-hosp-10005', 'HOSPITAL', '10005', '123456', 10005, 'Apollo Metro Hospital', 1, now(), 'seed'),
  ('login-hosp-10009', 'HOSPITAL', '10009', '123456', 10009, 'Ocean View Medical',    1, now(), 'seed')
ON CONFLICT (id) DO UPDATE SET
  password = EXCLUDED.password,
  status = 1,
  update_date = now(),
  update_user = 'seed';

-- Doctors: User ID = doctor_id, password 123456
INSERT INTO svc.login (
  id, login_type, login_id, password, hospital_id, doctor_id, display_name,
  status, creation_date, creation_user
)
SELECT
  'login-user-' || dp.doctor_id,
  'USER',
  dp.doctor_id,
  '123456',
  dc.hospital_id,
  dp.doctor_id,
  trim(both ' ' from coalesce(dp.first_name, '') || ' ' || coalesce(dp.last_name, '')),
  1,
  now(),
  'seed'
FROM svc.doctor_personal dp
LEFT JOIN svc.doctor_clinic dc ON dc.doctor_id = dp.doctor_id
WHERE dp.doctor_id LIKE 'DOC-SEED-%'
ON CONFLICT (id) DO UPDATE SET
  password = EXCLUDED.password,
  hospital_id = EXCLUDED.hospital_id,
  doctor_id = EXCLUDED.doctor_id,
  display_name = EXCLUDED.display_name,
  status = 1,
  update_date = now(),
  update_user = 'seed';

UPDATE svc.hospitals h
SET admin_password = '123456',
    updated_at = now()
WHERE h.id IN (10001, 10002, 10003, 10005, 10009);

UPDATE svc.doctor_personal
SET login_password = '123456',
    updated_date = now()
WHERE doctor_id LIKE 'DOC-SEED-%';
