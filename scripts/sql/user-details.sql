-- Run once if patient registration tables are missing
CREATE SCHEMA IF NOT EXISTS svc;

CREATE SEQUENCE IF NOT EXISTS svc.user_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS svc.user_details (
  user_id       VARCHAR(20)  PRIMARY KEY,
  user_name     VARCHAR(100) NOT NULL UNIQUE,
  password      VARCHAR(255) NOT NULL,
  created_by    VARCHAR(50),
  created_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by    VARCHAR(50),
  updated_date  TIMESTAMPTZ
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA svc TO medtrack;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA svc TO medtrack;
