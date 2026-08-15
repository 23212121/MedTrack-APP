-- Medical Visit Tracking App — PostgreSQL schema (MVP)
-- Compatible with Prisma models in ../prisma/schema.prisma

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- Enums
-- ---------------------------------------------------------------------------
CREATE TYPE user_role AS ENUM ('ADMIN', 'EMPLOYEE', 'DOCTOR', 'PATIENT');
CREATE TYPE visit_status AS ENUM (
  'CALLED', 'BOOKED', 'CHECKED_IN', 'IN_CONSULT', 'COMPLETED', 'NO_SHOW', 'CANCELLED'
);
CREATE TYPE note_type AS ENUM ('STAFF', 'CLINICAL', 'PATIENT_SUMMARY');
CREATE TYPE notification_channel AS ENUM ('SMS', 'EMAIL', 'PUSH');
CREATE TYPE notification_status AS ENUM ('PENDING', 'SENT', 'FAILED', 'SKIPPED');
CREATE TYPE gender AS ENUM ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN');

-- ---------------------------------------------------------------------------
-- Clinics (multi-branch ready)
-- ---------------------------------------------------------------------------
CREATE TABLE clinics (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name            TEXT NOT NULL,
  timezone        TEXT NOT NULL DEFAULT 'Asia/Kolkata',
  late_grace_minutes INT NOT NULL DEFAULT 10,
  max_delay_alerts_per_visit INT NOT NULL DEFAULT 2,
  you_are_next_threshold INT NOT NULL DEFAULT 2,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Users (auth for all roles)
-- ---------------------------------------------------------------------------
CREATE TABLE users (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id       UUID REFERENCES clinics(id) ON DELETE SET NULL,
  email           TEXT UNIQUE,
  phone           TEXT,
  password_hash   TEXT,
  role            user_role NOT NULL,
  full_name       TEXT NOT NULL,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT users_email_or_phone CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

CREATE INDEX idx_users_clinic ON users(clinic_id);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_role ON users(role);

-- ---------------------------------------------------------------------------
-- Departments & doctors
-- ---------------------------------------------------------------------------
CREATE TABLE departments (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
  name            TEXT NOT NULL,
  code            TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (clinic_id, name)
);

CREATE TABLE doctors (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
  user_id         UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  department_id   UUID REFERENCES departments(id) ON DELETE SET NULL,
  specialty       TEXT,
  avg_consult_minutes INT NOT NULL DEFAULT 15,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE doctor_schedules (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id       UUID NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
  day_of_week     INT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6), -- 0=Sunday
  start_time      TIME NOT NULL,
  end_time        TIME NOT NULL,
  slot_minutes    INT NOT NULL DEFAULT 15,
  UNIQUE (doctor_id, day_of_week, start_time)
);

CREATE TABLE schedule_slots (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id       UUID NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
  starts_at       TIMESTAMPTZ NOT NULL,
  ends_at         TIMESTAMPTZ NOT NULL,
  is_blocked      BOOLEAN NOT NULL DEFAULT FALSE,
  visit_id        UUID UNIQUE, -- filled when booked (FK added after visits)
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (doctor_id, starts_at)
);

-- ---------------------------------------------------------------------------
-- Patients (may share a phone — family members)
-- ---------------------------------------------------------------------------
CREATE TABLE patients (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
  user_id         UUID UNIQUE REFERENCES users(id) ON DELETE SET NULL,
  phone           TEXT NOT NULL,
  full_name       TEXT NOT NULL,
  date_of_birth   DATE,
  gender          gender NOT NULL DEFAULT 'UNKNOWN',
  email           TEXT,
  external_mrn    TEXT,
  sms_consent     BOOLEAN NOT NULL DEFAULT TRUE,
  email_consent   BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_patients_clinic_phone ON patients(clinic_id, phone);
CREATE INDEX idx_patients_name ON patients(clinic_id, full_name);

-- ---------------------------------------------------------------------------
-- Visits
-- ---------------------------------------------------------------------------
CREATE TABLE visits (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id           UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
  patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
  doctor_id           UUID REFERENCES doctors(id) ON DELETE SET NULL,
  department_id       UUID REFERENCES departments(id) ON DELETE SET NULL,
  status              visit_status NOT NULL DEFAULT 'CALLED',
  token_number        INT,
  reason              TEXT,
  scheduled_start     TIMESTAMPTZ,
  scheduled_end       TIMESTAMPTZ,
  checked_in_at       TIMESTAMPTZ,
  actual_start        TIMESTAMPTZ,
  actual_end          TIMESTAMPTZ,
  delay_minutes       INT NOT NULL DEFAULT 0,
  delay_alerts_sent   INT NOT NULL DEFAULT 0,
  created_by_user_id  UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_visits_clinic_day ON visits(clinic_id, scheduled_start);
CREATE INDEX idx_visits_patient ON visits(patient_id);
CREATE INDEX idx_visits_doctor_status ON visits(doctor_id, status);
CREATE INDEX idx_visits_status ON visits(clinic_id, status);

ALTER TABLE schedule_slots
  ADD CONSTRAINT fk_slots_visit
  FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------------
-- Timeline & notes
-- ---------------------------------------------------------------------------
CREATE TABLE visit_events (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  visit_id        UUID NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
  actor_user_id   UUID REFERENCES users(id) ON DELETE SET NULL,
  event_type      TEXT NOT NULL, -- STATUS_CHANGE, TOKEN_ISSUED, DELAY, NOTE, NOTIFY, etc.
  from_status     visit_status,
  to_status       visit_status,
  message         TEXT,
  metadata_json   JSONB,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_visit_events_visit ON visit_events(visit_id, created_at);

CREATE TABLE visit_notes (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  visit_id        UUID NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
  author_user_id  UUID REFERENCES users(id) ON DELETE SET NULL,
  note_type       note_type NOT NULL,
  body            TEXT NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_visit_notes_visit ON visit_notes(visit_id);

-- ---------------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------------
CREATE TABLE notification_templates (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
  event_code      TEXT NOT NULL,
  channel         notification_channel NOT NULL,
  subject         TEXT,
  body_template   TEXT NOT NULL,
  is_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (clinic_id, event_code, channel)
);

CREATE TABLE notifications (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id       UUID NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
  visit_id        UUID REFERENCES visits(id) ON DELETE SET NULL,
  patient_id      UUID REFERENCES patients(id) ON DELETE SET NULL,
  event_code      TEXT NOT NULL,
  channel         notification_channel NOT NULL,
  recipient       TEXT NOT NULL,
  subject         TEXT,
  body            TEXT NOT NULL,
  status          notification_status NOT NULL DEFAULT 'PENDING',
  provider_ref    TEXT,
  error_message   TEXT,
  scheduled_for   TIMESTAMPTZ,
  sent_at         TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_visit ON notifications(visit_id);
CREATE INDEX idx_notifications_pending ON notifications(status, scheduled_for);

-- ---------------------------------------------------------------------------
-- OTP challenges (patient login)
-- ---------------------------------------------------------------------------
CREATE TABLE otp_challenges (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  phone           TEXT NOT NULL,
  code_hash       TEXT NOT NULL,
  expires_at      TIMESTAMPTZ NOT NULL,
  consumed_at     TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_phone ON otp_challenges(phone, expires_at);
