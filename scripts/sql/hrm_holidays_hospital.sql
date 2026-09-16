-- Organization holidays shown to every employee of the hospital.
-- Existing svc.hrm_holidays is reused; hospital_id + reason are added.

ALTER TABLE svc.hrm_holidays
  ADD COLUMN IF NOT EXISTS hospital_id BIGINT;

ALTER TABLE svc.hrm_holidays
  ADD COLUMN IF NOT EXISTS reason VARCHAR(400);

CREATE INDEX IF NOT EXISTS idx_hrm_holidays_hospital_date
  ON svc.hrm_holidays (hospital_id, holiday_date);
