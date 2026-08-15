-- 4 patients sharing the same phone number (for /patient/reports dropdown demo)
-- Phone: 9829727230

CREATE TABLE IF NOT EXISTS svc.patient_reports (
  id            VARCHAR(36) PRIMARY KEY,
  patient_phone VARCHAR(32) NOT NULL,
  patient_name  VARCHAR(255),
  hospital_id   BIGINT,
  doctor_id     VARCHAR(64),
  doctor_name   VARCHAR(255),
  visit_id      VARCHAR(64),
  report_type   VARCHAR(32) NOT NULL,
  title         VARCHAR(500) NOT NULL,
  description   VARCHAR(2000),
  file_url      VARCHAR(1000),
  report_date   DATE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Clean prior seed for this demo phone
DELETE FROM svc.patient_reports
 WHERE patient_phone = '9829727230'
   AND id LIKE 'seed-rpt-%';

DELETE FROM svc.appointments
 WHERE phone_number = '9829727230'
   AND appointment_id LIKE 'seed-appt-shared-%';

INSERT INTO svc.appointments (
  appointment_id, hospital_id, doctor_id, patient_id, phone_number,
  patient_name, patient_age, gender, reason,
  appointment_date, appointment_time, token_number, status, booked_by,
  created_by, created_date
) VALUES
(
  'seed-appt-shared-01', 10001, 'DOC-SEED-0001', 'SEED-P-Ravi', '9829727230',
  'Ravi Sharma', 34, 'Male', 'Fever checkup',
  CURRENT_DATE, now() + interval '1 hour', 1, 'BOOKED', 'HOSPITAL',
  'seed', now()
),
(
  'seed-appt-shared-02', 10001, 'DOC-SEED-0001', 'SEED-P-Priya', '9829727230',
  'Priya Sharma', 28, 'Female', 'Follow-up',
  CURRENT_DATE, now() + interval '2 hours', 2, 'BOOKED', 'HOSPITAL',
  'seed', now()
),
(
  'seed-appt-shared-03', 10001, 'DOC-SEED-0002', 'SEED-P-Aman', '9829727230',
  'Aman Sharma', 10, 'Male', 'Pediatric visit',
  CURRENT_DATE, now() + interval '3 hours', 3, 'BOOKED', 'HOSPITAL',
  'seed', now()
),
(
  'seed-appt-shared-04', 10001, 'DOC-SEED-0002', 'SEED-P-Meera', '9829727230',
  'Meera Sharma', 55, 'Female', 'Blood pressure',
  CURRENT_DATE, now() + interval '4 hours', 4, 'BOOKED', 'HOSPITAL',
  'seed', now()
);

INSERT INTO svc.patient_reports (
  id, patient_phone, patient_name, hospital_id, doctor_id, doctor_name,
  report_type, title, description, file_url, report_date, created_at
) VALUES
(
  'seed-rpt-ravi-lab', '9829727230', 'Ravi Sharma', 10001, 'DOC-SEED-0001', 'Dr. Seed',
  'LAB', 'CBC Report - Ravi', 'Complete blood count for Ravi Sharma',
  '/files/seed-ravi-cbc.pdf', CURRENT_DATE - 2, now()
),
(
  'seed-rpt-ravi-rx', '9829727230', 'Ravi Sharma', 10001, 'DOC-SEED-0001', 'Dr. Seed',
  'PRESCRIPTION', 'Prescription - Ravi', 'Medicines for fever',
  '/files/seed-ravi-rx.pdf', CURRENT_DATE - 1, now()
),
(
  'seed-rpt-priya-lab', '9829727230', 'Priya Sharma', 10001, 'DOC-SEED-0001', 'Dr. Seed',
  'LAB', 'Thyroid Panel - Priya', 'TSH / T3 / T4 for Priya Sharma',
  '/files/seed-priya-thyroid.pdf', CURRENT_DATE - 3, now()
),
(
  'seed-rpt-aman-img', '9829727230', 'Aman Sharma', 10001, 'DOC-SEED-0002', 'Dr. Seed',
  'IMAGING', 'X-Ray Chest - Aman', 'Pediatric chest x-ray',
  '/files/seed-aman-xray.pdf', CURRENT_DATE - 1, now()
),
(
  'seed-rpt-meera-dis', '9829727230', 'Meera Sharma', 10001, 'DOC-SEED-0002', 'Dr. Seed',
  'DISCHARGE', 'Discharge Summary - Meera', 'BP monitoring visit summary',
  '/files/seed-meera-discharge.pdf', CURRENT_DATE, now()
);

-- Optional: ensure a login identity exists for this phone (user_details)
INSERT INTO svc.user_details (user_id, user_name, phone, password, created_date)
SELECT 'USRSEED01', 'SharedPhoneDemo', '9829727230', 'demo1234', now()
WHERE NOT EXISTS (
  SELECT 1 FROM svc.user_details WHERE phone = '9829727230'
);
