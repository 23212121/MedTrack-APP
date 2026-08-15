-- MedTrack Spring/JPA tables used by React web UI forms (schema: svc)
-- Column names match SpringPhysicalNamingStrategy (camelCase -> snake_case)

CREATE SCHEMA IF NOT EXISTS svc;
GRANT ALL ON SCHEMA svc TO medtrack;
ALTER DEFAULT PRIVILEGES IN SCHEMA svc GRANT ALL ON TABLES TO medtrack;
ALTER DEFAULT PRIVILEGES IN SCHEMA svc GRANT ALL ON SEQUENCES TO medtrack;

-- Bookings page form (normalized — hospital/doctor/fee resolved at read time)
CREATE TABLE IF NOT EXISTS svc.bookings (
  id                VARCHAR(36) PRIMARY KEY,
  hospital_id       BIGINT       NOT NULL,
  doctor_id         VARCHAR(64)  NOT NULL,
  patient_name      VARCHAR(255) NOT NULL,
  patient_phone     VARCHAR(32)  NOT NULL,
  patient_age       INTEGER,
  gender            VARCHAR(32),
  address           VARCHAR(500),
  reason            VARCHAR(1000),
  appointment_date  DATE         NOT NULL,
  appointment_time  TIMESTAMPTZ  NOT NULL,
  token_number      INTEGER,
  status            VARCHAR(32)  NOT NULL DEFAULT 'BOOKED',
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ
);

-- Primary appointment store (FKs only — display fields from hospitals/doctors)
CREATE TABLE IF NOT EXISTS svc.appointments (
  appointment_id    VARCHAR(36)  PRIMARY KEY,
  hospital_id       BIGINT,
  doctor_id         VARCHAR(64)  NOT NULL,
  patient_id        VARCHAR(32)  NOT NULL,
  patient_name      VARCHAR(255) NOT NULL,
  patient_age       INTEGER,
  gender            VARCHAR(32),
  address           VARCHAR(500),
  reason            VARCHAR(1000),
  appointment_date  DATE         NOT NULL,
  appointment_time  TIMESTAMPTZ  NOT NULL,
  token_number      INTEGER,
  status            VARCHAR(32)  NOT NULL DEFAULT 'BOOKED',
  booked_by         VARCHAR(32),
  booking_ref_id    VARCHAR(36),
  created_by        VARCHAR(50),
  created_date      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by        VARCHAR(50),
  updated_date      TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_appointments_patient ON svc.appointments(patient_id);
CREATE INDEX IF NOT EXISTS idx_appointments_doctor_date ON svc.appointments(doctor_id, appointment_date);
CREATE INDEX IF NOT EXISTS idx_appointments_hospital ON svc.appointments(hospital_id);

-- Check-in / Doctor queue / Fees
CREATE TABLE IF NOT EXISTS svc.visits (
  id                VARCHAR(36) PRIMARY KEY,
  clinic_id         VARCHAR(64),
  patient_id        VARCHAR(64),
  patient_name      VARCHAR(255),
  patient_phone     VARCHAR(32),
  patient_email     VARCHAR(255),
  sms_consent       BOOLEAN NOT NULL DEFAULT TRUE,
  email_consent     BOOLEAN NOT NULL DEFAULT TRUE,
  doctor_id         VARCHAR(64),
  doctor_name       VARCHAR(255),
  status            VARCHAR(32) NOT NULL DEFAULT 'BOOKED',
  token_number      INTEGER,
  reason            VARCHAR(1000),
  scheduled_start   TIMESTAMPTZ,
  scheduled_end     TIMESTAMPTZ,
  checked_in_at     TIMESTAMPTZ,
  actual_start      TIMESTAMPTZ,
  actual_end        TIMESTAMPTZ,
  delay_minutes     INTEGER NOT NULL DEFAULT 0,
  base_fee          DOUBLE PRECISION,
  overtime_minutes  INTEGER NOT NULL DEFAULT 0,
  overtime_fee      DOUBLE PRECISION NOT NULL DEFAULT 0,
  total_fee         DOUBLE PRECISION,
  fee_currency      VARCHAR(8) DEFAULT 'INR',
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS svc.visit_events (
  id           VARCHAR(36) PRIMARY KEY,
  hospital_id  BIGINT NOT NULL DEFAULT 10001,
  visit_id     VARCHAR(36),
  event_type   VARCHAR(64),
  from_status  VARCHAR(32),
  to_status    VARCHAR(32),
  message      VARCHAR(1000),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Schedules page
CREATE TABLE IF NOT EXISTS svc.doctor_schedules (
  id            VARCHAR(36) PRIMARY KEY,
  doctor_id     VARCHAR(64),
  day_of_week   INTEGER NOT NULL,
  start_time    VARCHAR(16),
  end_time      VARCHAR(16),
  slot_minutes  INTEGER NOT NULL DEFAULT 15,
  CONSTRAINT uq_svc_doctor_schedules UNIQUE (doctor_id, day_of_week, start_time)
);

-- Availability page
CREATE TABLE IF NOT EXISTS svc.doctor_availability (
  id                 VARCHAR(36) PRIMARY KEY,
  doctor_id          VARCHAR(64),
  starts_at          TIMESTAMPTZ,
  ends_at            TIMESTAMPTZ,
  availability_type  VARCHAR(32) DEFAULT 'AVAILABLE',
  reason             VARCHAR(500),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Fees page
CREATE TABLE IF NOT EXISTS svc.fee_rules (
  id                              VARCHAR(64) PRIMARY KEY,
  clinic_id                       VARCHAR(64),
  doctor_id                       VARCHAR(64),
  base_consult_fee                DOUBLE PRECISION NOT NULL DEFAULT 500,
  fixed_consult_minutes           INTEGER NOT NULL DEFAULT 15,
  overtime_fee_amount             DOUBLE PRECISION NOT NULL DEFAULT 200,
  overtime_fee_per_block_minutes  INTEGER NOT NULL DEFAULT 15,
  currency                        VARCHAR(8) DEFAULT 'INR'
);

-- Notifications page
CREATE TABLE IF NOT EXISTS svc.notifications (
  id             VARCHAR(36) PRIMARY KEY,
  clinic_id      VARCHAR(64),
  visit_id       VARCHAR(64),
  patient_id     VARCHAR(64),
  event_code     VARCHAR(64),
  channel        VARCHAR(16),
  recipient      VARCHAR(255),
  subject        VARCHAR(255),
  body           VARCHAR(4000),
  status         VARCHAR(32) DEFAULT 'PENDING',
  provider_ref   VARCHAR(255),
  error_message  VARCHAR(1000),
  sent_at        TIMESTAMPTZ,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Patient queue / waiting time
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

-- Patient portal accounts
CREATE TABLE IF NOT EXISTS svc.patients (
  id                 VARCHAR(36) PRIMARY KEY,
  phone              VARCHAR(32)  NOT NULL UNIQUE,
  password           VARCHAR(255) NOT NULL,
  name               VARCHAR(255) NOT NULL,
  age                INTEGER,
  gender             VARCHAR(32),
  email              VARCHAR(255),
  address            VARCHAR(500),
  blood_group        VARCHAR(8),
  emergency_contact  VARCHAR(32),
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ
);

-- Medical reports for patient portal
CREATE TABLE IF NOT EXISTS svc.patient_reports (
  id             VARCHAR(36) PRIMARY KEY,
  patient_phone  VARCHAR(32)  NOT NULL,
  patient_name   VARCHAR(255),
  hospital_id    BIGINT,
  doctor_id      VARCHAR(64),
  doctor_name    VARCHAR(255),
  visit_id       VARCHAR(64),
  report_type    VARCHAR(32)  NOT NULL,
  title          VARCHAR(500) NOT NULL,
  description    VARCHAR(2000),
  file_url       VARCHAR(1000),
  report_date    DATE,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Patient/User registration (USER_DETAILS spec)
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
