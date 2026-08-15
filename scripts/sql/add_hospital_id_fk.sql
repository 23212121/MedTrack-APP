-- Add hospital_id to all svc tables that lack it.
-- hospitals.id remains PRIMARY KEY; hospital_id on hospitals mirrors id (UNIQUE).
-- Other tables: hospital_id is FOREIGN KEY -> svc.hospitals(id).

BEGIN;

-- 1) hospitals: naming column hospital_id (= id), unique
ALTER TABLE svc.hospitals
  ADD COLUMN IF NOT EXISTS hospital_id BIGINT;

UPDATE svc.hospitals
SET hospital_id = id
WHERE hospital_id IS NULL;

ALTER TABLE svc.hospitals
  ALTER COLUMN hospital_id SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uk_hospitals_hospital_id'
  ) THEN
    ALTER TABLE svc.hospitals
      ADD CONSTRAINT uk_hospitals_hospital_id UNIQUE (hospital_id);
  END IF;
END $$;

-- Keep hospital_id in sync with id for new/updated rows
CREATE OR REPLACE FUNCTION svc.trg_hospitals_sync_hospital_id()
RETURNS trigger AS $$
BEGIN
  NEW.hospital_id := NEW.id;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS hospitals_sync_hospital_id ON svc.hospitals;
CREATE TRIGGER hospitals_sync_hospital_id
  BEFORE INSERT OR UPDATE OF id ON svc.hospitals
  FOR EACH ROW
  EXECUTE FUNCTION svc.trg_hospitals_sync_hospital_id();

-- Helper: ensure default hospital exists for backfill
INSERT INTO svc.hospitals (
  id, hospital_id, hospital_code, hospital_name, status, created_at, updated_at
)
SELECT 10001, 10001, '10001', 'Test Hospital', 'APPROVED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM svc.hospitals WHERE id = 10001);

-- 2) Add hospital_id columns (nullable first, then backfill, then FK)
ALTER TABLE svc.bookings            ADD COLUMN IF NOT EXISTS hospital_id BIGINT;
ALTER TABLE svc.visits              ADD COLUMN IF NOT EXISTS hospital_id BIGINT;
ALTER TABLE svc.visit_events        ADD COLUMN IF NOT EXISTS hospital_id BIGINT;
ALTER TABLE svc.notifications       ADD COLUMN IF NOT EXISTS hospital_id BIGINT;
ALTER TABLE svc.doctor_personal     ADD COLUMN IF NOT EXISTS hospital_id BIGINT;
ALTER TABLE svc.doctor_contact      ADD COLUMN IF NOT EXISTS hospital_id BIGINT;
ALTER TABLE svc.doctor_bank         ADD COLUMN IF NOT EXISTS hospital_id BIGINT;
ALTER TABLE svc.doctor_documents    ADD COLUMN IF NOT EXISTS hospital_id BIGINT;
ALTER TABLE svc.doctor_identity     ADD COLUMN IF NOT EXISTS hospital_id BIGINT;
ALTER TABLE svc.doctor_professional ADD COLUMN IF NOT EXISTS hospital_id BIGINT;
ALTER TABLE svc.doctor_schedules    ADD COLUMN IF NOT EXISTS hospital_id BIGINT;
ALTER TABLE svc.doctor_availability ADD COLUMN IF NOT EXISTS hospital_id BIGINT;
ALTER TABLE svc.fee_rules           ADD COLUMN IF NOT EXISTS hospital_id BIGINT;

-- Ensure doctor_clinic / doctor_queue / doctors / user already have column; add FK later

-- 3) Backfill
UPDATE svc.bookings b
SET hospital_id = CASE
  WHEN b.clinic_id ~ '^[0-9]+$' THEN b.clinic_id::bigint
  ELSE 10001
END
WHERE b.hospital_id IS NULL;

UPDATE svc.visits v
SET hospital_id = CASE
  WHEN v.clinic_id ~ '^[0-9]+$' THEN v.clinic_id::bigint
  ELSE 10001
END
WHERE v.hospital_id IS NULL;

UPDATE svc.visit_events e
SET hospital_id = COALESCE(
  (SELECT v.hospital_id FROM svc.visits v WHERE v.id = e.visit_id),
  10001
)
WHERE e.hospital_id IS NULL;

UPDATE svc.notifications n
SET hospital_id = CASE
  WHEN n.clinic_id ~ '^[0-9]+$' THEN n.clinic_id::bigint
  ELSE 10001
END
WHERE n.hospital_id IS NULL;

UPDATE svc.doctor_personal p
SET hospital_id = COALESCE(
  (SELECT c.hospital_id FROM svc.doctor_clinic c WHERE c.doctor_id = p.doctor_id),
  10001
)
WHERE p.hospital_id IS NULL;

UPDATE svc.doctor_contact t
SET hospital_id = COALESCE(
  (SELECT c.hospital_id FROM svc.doctor_clinic c WHERE c.doctor_id = t.doctor_id),
  10001
)
WHERE t.hospital_id IS NULL;

UPDATE svc.doctor_bank t
SET hospital_id = COALESCE(
  (SELECT c.hospital_id FROM svc.doctor_clinic c WHERE c.doctor_id = t.doctor_id),
  10001
)
WHERE t.hospital_id IS NULL;

UPDATE svc.doctor_documents t
SET hospital_id = COALESCE(
  (SELECT c.hospital_id FROM svc.doctor_clinic c WHERE c.doctor_id = t.doctor_id),
  10001
)
WHERE t.hospital_id IS NULL;

UPDATE svc.doctor_identity t
SET hospital_id = COALESCE(
  (SELECT c.hospital_id FROM svc.doctor_clinic c WHERE c.doctor_id = t.doctor_id),
  10001
)
WHERE t.hospital_id IS NULL;

UPDATE svc.doctor_professional t
SET hospital_id = COALESCE(
  (SELECT c.hospital_id FROM svc.doctor_clinic c WHERE c.doctor_id = t.doctor_id),
  10001
)
WHERE t.hospital_id IS NULL;

UPDATE svc.doctor_schedules s
SET hospital_id = COALESCE(
  (SELECT c.hospital_id FROM svc.doctor_clinic c WHERE c.doctor_id = s.doctor_id),
  10001
)
WHERE s.hospital_id IS NULL;

UPDATE svc.doctor_availability a
SET hospital_id = COALESCE(
  (SELECT c.hospital_id FROM svc.doctor_clinic c WHERE c.doctor_id = a.doctor_id),
  10001
)
WHERE a.hospital_id IS NULL;

UPDATE svc.fee_rules f
SET hospital_id = COALESCE(
  (SELECT c.hospital_id FROM svc.doctor_clinic c WHERE c.doctor_id = f.doctor_id),
  CASE WHEN f.clinic_id ~ '^[0-9]+$' THEN f.clinic_id::bigint ELSE 10001 END
)
WHERE f.hospital_id IS NULL;

UPDATE svc.doctor_clinic c
SET hospital_id = 10001
WHERE c.hospital_id IS NULL;

UPDATE svc.doctor_queue q
SET hospital_id = 10001
WHERE q.hospital_id IS NULL;

UPDATE svc.doctors d
SET hospital_id = COALESCE(d.hospital_id, 10001)
WHERE d.hospital_id IS NULL;

-- Fix orphan hospital_ids that don't exist in hospitals
UPDATE svc.bookings SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.visits SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.visit_events SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.notifications SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.doctor_personal SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.doctor_contact SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.doctor_bank SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.doctor_documents SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.doctor_identity SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.doctor_professional SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.doctor_schedules SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.doctor_availability SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.fee_rules SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.doctor_clinic SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.doctor_queue SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);
UPDATE svc.doctors SET hospital_id = 10001
WHERE hospital_id IS NOT NULL AND hospital_id NOT IN (SELECT id FROM svc.hospitals);

-- 4) NOT NULL where appropriate
ALTER TABLE svc.bookings            ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.visits              ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.visit_events        ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.notifications       ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.doctor_personal     ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.doctor_contact      ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.doctor_bank         ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.doctor_documents    ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.doctor_identity     ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.doctor_professional ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.doctor_schedules    ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.doctor_availability ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.fee_rules           ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.doctor_clinic       ALTER COLUMN hospital_id SET NOT NULL;
ALTER TABLE svc.doctor_queue        ALTER COLUMN hospital_id SET NOT NULL;

UPDATE svc."user" u
SET hospital_id = COALESCE(
  u.hospital_id,
  (SELECT c.hospital_id FROM svc.doctor_clinic c WHERE c.doctor_id = u.doctor_id),
  10001
)
WHERE u.hospital_id IS NULL OR u.hospital_id NOT IN (SELECT id FROM svc.hospitals);

-- 5) Foreign keys -> hospitals(id)
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT * FROM (VALUES
      ('bookings', 'fk_bookings_hospital'),
      ('visits', 'fk_visits_hospital'),
      ('visit_events', 'fk_visit_events_hospital'),
      ('notifications', 'fk_notifications_hospital'),
      ('doctor_personal', 'fk_doctor_personal_hospital'),
      ('doctor_contact', 'fk_doctor_contact_hospital'),
      ('doctor_bank', 'fk_doctor_bank_hospital'),
      ('doctor_documents', 'fk_doctor_documents_hospital'),
      ('doctor_identity', 'fk_doctor_identity_hospital'),
      ('doctor_professional', 'fk_doctor_professional_hospital'),
      ('doctor_schedules', 'fk_doctor_schedules_hospital'),
      ('doctor_availability', 'fk_doctor_availability_hospital'),
      ('fee_rules', 'fk_fee_rules_hospital'),
      ('doctor_clinic', 'fk_doctor_clinic_hospital'),
      ('doctor_queue', 'fk_doctor_queue_hospital'),
      ('doctors', 'fk_doctors_hospital'),
      ('user', 'fk_user_hospital')
    ) AS t(tbl, cname)
  LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = r.cname) THEN
      EXECUTE format(
        'ALTER TABLE svc.%I ADD CONSTRAINT %I FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)',
        r.tbl, r.cname
      );
    END IF;
  END LOOP;
END $$;

-- Indexes for hospital-scoped queries
CREATE INDEX IF NOT EXISTS idx_bookings_hospital_id ON svc.bookings(hospital_id);
CREATE INDEX IF NOT EXISTS idx_visits_hospital_id ON svc.visits(hospital_id);
CREATE INDEX IF NOT EXISTS idx_doctor_personal_hospital_id ON svc.doctor_personal(hospital_id);
CREATE INDEX IF NOT EXISTS idx_doctor_schedules_hospital_id ON svc.doctor_schedules(hospital_id);
CREATE INDEX IF NOT EXISTS idx_doctor_availability_hospital_id ON svc.doctor_availability(hospital_id);

COMMIT;

-- Summary
SELECT t.table_name,
       CASE WHEN c.column_name IS NULL THEN 'NO' ELSE 'YES' END AS has_hospital_id
FROM information_schema.tables t
LEFT JOIN information_schema.columns c
  ON c.table_schema = t.table_schema
 AND c.table_name = t.table_name
 AND c.column_name = 'hospital_id'
WHERE t.table_schema = 'svc' AND t.table_type = 'BASE TABLE'
ORDER BY t.table_name;
