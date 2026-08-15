-- Seed data matching MedTrack React UI fields (schema: svc)

TRUNCATE TABLE
  svc.notifications,
  svc.visit_events,
  svc.visits,
  svc.bookings,
  svc.doctor_availability,
  svc.doctor_schedules,
  svc.fee_rules
RESTART IDENTITY CASCADE;

INSERT INTO svc.fee_rules (
  id, clinic_id, doctor_id, base_consult_fee, fixed_consult_minutes,
  overtime_fee_amount, overtime_fee_per_block_minutes, currency
) VALUES
  ('fee-seed-doctor-1', 'seed-clinic-1', 'seed-doctor-1', 500, 15, 200, 15, 'INR');

INSERT INTO svc.doctor_schedules (id, doctor_id, day_of_week, start_time, end_time, slot_minutes) VALUES
  ('sch-1', 'seed-doctor-1', 1, '09:00', '13:00', 15),
  ('sch-2', 'seed-doctor-1', 1, '14:00', '18:00', 15),
  ('sch-3', 'seed-doctor-1', 2, '09:00', '13:00', 15),
  ('sch-4', 'seed-doctor-1', 2, '14:00', '18:00', 15),
  ('sch-5', 'seed-doctor-1', 3, '09:00', '13:00', 15),
  ('sch-6', 'seed-doctor-1', 3, '14:00', '18:00', 15),
  ('sch-7', 'seed-doctor-1', 4, '09:00', '13:00', 15),
  ('sch-8', 'seed-doctor-1', 4, '14:00', '18:00', 15),
  ('sch-9', 'seed-doctor-1', 5, '09:00', '13:00', 15),
  ('sch-10', 'seed-doctor-1', 5, '14:00', '18:00', 15),
  ('sch-11', 'seed-doctor-1', 6, '09:00', '13:00', 15);

INSERT INTO svc.doctor_availability (
  id, doctor_id, starts_at, ends_at, availability_type, reason, created_at
) VALUES
  (
    'avail-1',
    'seed-doctor-1',
    date_trunc('day', now()) + INTERVAL '1 day' + TIME '12:00',
    date_trunc('day', now()) + INTERVAL '1 day' + TIME '13:00',
    'BUSY',
    'Lunch / admin',
    now()
  );

INSERT INTO svc.bookings (
  id, clinic_id, clinic_name, doctor_id, doctor_name,
  patient_name, patient_phone, patient_age, gender, address, reason,
  appointment_date, appointment_time, token_number, status,
  consultation_fee, currency, created_at, updated_at
) VALUES
  (
    'book-1', 'seed-clinic-1', 'Sunrise Care Clinic', 'seed-doctor-1', 'Dr. Mehta',
    'Anita Sharma', '9876543210', 36, 'FEMALE', '12 MG Road, Bengaluru', 'Fever and cough',
    CURRENT_DATE, date_trunc('day', now()) + TIME '10:00', 1, 'BOOKED', 500, 'INR', now(), now()
  ),
  (
    'book-2', 'seed-clinic-1', 'Sunrise Care Clinic', 'seed-doctor-1', 'Dr. Mehta',
    'Rohan Sharma', '9876543210', 10, 'MALE', '12 MG Road, Bengaluru', 'Follow-up vaccination',
    CURRENT_DATE, date_trunc('day', now()) + TIME '10:30', 2, 'BOOKED', 500, 'INR', now(), now()
  );

INSERT INTO svc.visits (
  id, clinic_id, patient_id, patient_name, patient_phone, patient_email,
  sms_consent, email_consent, doctor_id, doctor_name, status, token_number, reason,
  scheduled_start, scheduled_end, checked_in_at, delay_minutes,
  base_fee, overtime_minutes, overtime_fee, total_fee, fee_currency,
  created_at, updated_at
) VALUES
  (
    'visit-1', 'seed-clinic-1', 'seed-patient-1', 'Anita Sharma', '9876543210', 'anita@example.com',
    TRUE, TRUE, 'seed-doctor-1', 'Dr. Mehta', 'CHECKED_IN', 1, 'Fever and cough',
    date_trunc('day', now()) + TIME '10:00', date_trunc('day', now()) + TIME '10:15',
    now() - INTERVAL '20 minutes', 0, 500, 0, 0, 500, 'INR', now(), now()
  ),
  (
    'visit-2', 'seed-clinic-1', 'seed-patient-child', 'Rohan Sharma', '9876543210', NULL,
    TRUE, FALSE, 'seed-doctor-1', 'Dr. Mehta', 'BOOKED', 2, 'Follow-up vaccination',
    date_trunc('day', now()) + TIME '10:30', date_trunc('day', now()) + TIME '10:45',
    NULL, 0, 500, 0, 0, NULL, 'INR', now(), now()
  ),
  (
    'visit-3', 'seed-clinic-1', NULL, 'Vikram Patel', '9988776655', 'vikram@example.com',
    TRUE, TRUE, 'seed-doctor-1', 'Dr. Mehta', 'IN_CONSULT', 3, 'Blood pressure review',
    date_trunc('day', now()) + TIME '09:30', date_trunc('day', now()) + TIME '09:45',
    now() - INTERVAL '45 minutes', 5, 500, 0, 0, NULL, 'INR', now(), now()
  );

UPDATE svc.visits
SET actual_start = now() - INTERVAL '10 minutes'
WHERE id = 'visit-3';

INSERT INTO svc.visit_events (id, visit_id, event_type, from_status, to_status, message, created_at) VALUES
  ('ve-1', 'visit-1', 'STATUS_CHANGE', 'BOOKED', 'CHECKED_IN', 'Patient checked in at desk', now() - INTERVAL '20 minutes'),
  ('ve-2', 'visit-3', 'STATUS_CHANGE', 'CHECKED_IN', 'IN_CONSULT', 'Doctor started consult', now() - INTERVAL '10 minutes');

INSERT INTO svc.notifications (
  id, clinic_id, visit_id, patient_id, event_code, channel, recipient,
  subject, body, status, sent_at, created_at
) VALUES
  (
    'notif-1', 'seed-clinic-1', 'visit-1', 'seed-patient-1', 'CHECK_IN_CONFIRM', 'SMS', '9876543210',
    NULL, 'Anita Sharma, you are checked in. Token #1 with Dr. Mehta.',
    'SENT', now() - INTERVAL '19 minutes', now() - INTERVAL '19 minutes'
  ),
  (
    'notif-2', 'seed-clinic-1', 'visit-2', 'seed-patient-child', 'APPOINTMENT_REMINDER', 'SMS', '9876543210',
    NULL, 'Reminder: Rohan Sharma has an appointment today at 10:30 with Dr. Mehta.',
    'PENDING', NULL, now()
  );
