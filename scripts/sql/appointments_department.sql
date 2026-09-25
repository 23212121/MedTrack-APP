-- Snapshot doctor department on appointments so booked rows can be filtered.
ALTER TABLE IF EXISTS svc.appointments
  ADD COLUMN IF NOT EXISTS department character varying(255);

UPDATE svc.appointments a
SET department = p.department
FROM svc.doctor_professional p
WHERE a.doctor_id = p.doctor_id
  AND (a.department IS NULL OR a.department = '')
  AND p.department IS NOT NULL
  AND btrim(p.department) <> '';

CREATE INDEX IF NOT EXISTS idx_appointments_department ON svc.appointments (department);
