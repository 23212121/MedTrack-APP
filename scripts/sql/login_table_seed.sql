-- Hospital login credentials (svc.login) + test data
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

CREATE INDEX IF NOT EXISTS idx_login_hospital ON svc.login (hospital_id);

-- Test hospital logins
INSERT INTO svc.login (
  id, login_type, login_id, password, hospital_id, display_name,
  status, creation_date, creation_user
) VALUES
  ('login-hosp-10001', 'HOSPITAL', '10001', 'admin',       10001, 'Test Hospital',          1, now(), 'seed'),
  ('login-hosp-10002', 'HOSPITAL', '10002', 'hospital123', 10002, 'Sunrise Care Hospital',  1, now(), 'seed'),
  ('login-hosp-10003', 'HOSPITAL', '10003', 'hospital123', 10003, 'City Heart Institute',   1, now(), 'seed'),
  ('login-hosp-10005', 'HOSPITAL', '10005', 'hospital123', 10005, 'Apollo Metro Hospital',  1, now(), 'seed'),
  ('login-hosp-10009', 'HOSPITAL', '10009', 'demo123',     10009, 'Ocean View Medical',     1, now(), 'seed')
ON CONFLICT (id) DO UPDATE SET
  password = EXCLUDED.password,
  display_name = EXCLUDED.display_name,
  status = 1,
  update_date = now(),
  update_user = 'seed';

-- Keep hospitals.admin_password in sync for legacy fallback
UPDATE svc.hospitals h
SET admin_password = l.password,
    admin_email = COALESCE(NULLIF(h.admin_email, ''), 'admin@hospital-' || h.id || '.local'),
    updated_at = now()
FROM svc.login l
WHERE l.login_type = 'HOSPITAL'
  AND l.hospital_id = h.id
  AND l.status = 1;
