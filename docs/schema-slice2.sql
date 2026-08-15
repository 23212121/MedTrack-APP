-- Slice 2+: doctor availability, overtime fees, checkup notification extras
-- Apply after schema.sql

ALTER TABLE clinics
  ADD COLUMN IF NOT EXISTS fixed_consult_minutes INT NOT NULL DEFAULT 15,
  ADD COLUMN IF NOT EXISTS overtime_fee_amount NUMERIC(12, 2) NOT NULL DEFAULT 200.00,
  ADD COLUMN IF NOT EXISTS overtime_fee_currency TEXT NOT NULL DEFAULT 'INR',
  ADD COLUMN IF NOT EXISTS overtime_fee_per_block_minutes INT NOT NULL DEFAULT 15;

ALTER TABLE doctors
  ADD COLUMN IF NOT EXISTS base_consult_fee NUMERIC(12, 2) NOT NULL DEFAULT 500.00,
  ADD COLUMN IF NOT EXISTS fixed_consult_minutes INT,
  ADD COLUMN IF NOT EXISTS overtime_fee_amount NUMERIC(12, 2);

-- Explicit availability / leave / busy blocks (beyond weekly doctor_schedules)
CREATE TABLE IF NOT EXISTS doctor_availability (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id       UUID NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
  starts_at       TIMESTAMPTZ NOT NULL,
  ends_at         TIMESTAMPTZ NOT NULL,
  availability_type TEXT NOT NULL DEFAULT 'AVAILABLE',
  -- AVAILABLE | BUSY | LEAVE | BLOCKED
  reason          TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (ends_at > starts_at)
);

CREATE INDEX IF NOT EXISTS idx_doctor_availability_doctor_range
  ON doctor_availability(doctor_id, starts_at, ends_at);

ALTER TABLE visits
  ADD COLUMN IF NOT EXISTS base_fee NUMERIC(12, 2),
  ADD COLUMN IF NOT EXISTS overtime_minutes INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS overtime_fee NUMERIC(12, 2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS total_fee NUMERIC(12, 2),
  ADD COLUMN IF NOT EXISTS fee_currency TEXT DEFAULT 'INR';

CREATE TABLE IF NOT EXISTS visit_fee_charges (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  visit_id        UUID NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
  charge_type     TEXT NOT NULL, -- BASE | OVERTIME
  description     TEXT,
  amount          NUMERIC(12, 2) NOT NULL,
  currency        TEXT NOT NULL DEFAULT 'INR',
  minutes_over    INT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_visit_fee_charges_visit ON visit_fee_charges(visit_id);
