-- Indexes on svc.appointments for common lookups
-- doctor_id, hospital_id, phone_number, patient_id

CREATE INDEX IF NOT EXISTS idx_appointments_doctor
  ON svc.appointments (doctor_id);

CREATE INDEX IF NOT EXISTS idx_appointments_hospital
  ON svc.appointments (hospital_id);

CREATE INDEX IF NOT EXISTS idx_appointments_phone
  ON svc.appointments (phone_number);

CREATE INDEX IF NOT EXISTS idx_appointments_patient
  ON svc.appointments (patient_id);

-- Composite helpers for queue / hospital boards
CREATE INDEX IF NOT EXISTS idx_appointments_doctor_date
  ON svc.appointments (doctor_id, appointment_date);

CREATE INDEX IF NOT EXISTS idx_appointments_hospital_doctor
  ON svc.appointments (hospital_id, doctor_id);
