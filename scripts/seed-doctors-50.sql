-- Ensure master doctors table exists with hospital_id (hospitals start at 10001)
CREATE TABLE IF NOT EXISTS svc.doctors (
  doctor_id          varchar(40) PRIMARY KEY,
  first_name         varchar(255) NOT NULL,
  last_name          varchar(255) NOT NULL,
  middle_name        varchar(255),
  gender             varchar(40),
  specialization     varchar(255),
  department         varchar(255),
  qualification      varchar(255),
  email              varchar(255),
  mobile_number      varchar(40),
  hospital_id        bigint NOT NULL,
  hospital_name      varchar(255),
  clinic_name        varchar(255),
  consultation_fee   double precision,
  status             varchar(40) NOT NULL DEFAULT 'ACTIVE',
  created_at         timestamptz NOT NULL DEFAULT NOW(),
  updated_at         timestamptz NOT NULL DEFAULT NOW()
);

-- Add hospital_id if table already existed without it
ALTER TABLE svc.doctors
  ADD COLUMN IF NOT EXISTS hospital_id bigint;

ALTER TABLE svc.doctor_clinic
  ADD COLUMN IF NOT EXISTS hospital_id bigint;

-- Seed 50 doctors across hospitals 10001–10011
WITH hospitals AS (
  SELECT id, hospital_name
  FROM svc.hospitals
  WHERE id >= 10001
  ORDER BY id
),
hosp_arr AS (
  SELECT array_agg(id ORDER BY id) AS ids,
         array_agg(hospital_name ORDER BY id) AS names
  FROM hospitals
),
seed AS (
  SELECT
    g AS n,
    'DOC-SEED-' || lpad(g::text, 4, '0') AS doctor_id,
    (ARRAY[
      'Aarav','Vivaan','Aditya','Vihaan','Arjun','Sai','Reyansh','Ayaan','Krishna','Ishaan',
      'Ananya','Aadhya','Diya','Myra','Sara','Ira','Anika','Navya','Pari','Kiara',
      'Rohan','Kabir','Dev','Yash','Om','Neil','Aryan','Kunal','Harsh','Rahul',
      'Neha','Pooja','Sneha','Riya','Meera','Kavya','Isha','Tanvi','Nisha','Shruti',
      'Amit','Suresh','Vikram','Manish','Pranav','Nikhil','Gaurav','Abhishek','Siddharth','Kartik'
    ])[g] AS first_name,
    (ARRAY[
      'Sharma','Verma','Patel','Reddy','Nair','Iyer','Khan','Singh','Das','Mehta',
      'Joshi','Gupta','Malhotra','Chopra','Bose','Banerjee','Pillai','Rao','Shah','Kapoor',
      'Agarwal','Jain','Desai','Kulkarni','Chauhan','Yadav','Mishra','Pandey','Saxena','Bhat',
      'Menon','Shetty','Kaur','Gill','Bansal','Tiwari','Dubey','Rathore','Solanki','Trivedi',
      'Chatterjee','Mukherjee','Ghosh','Sen','Paul','Dutta','Naidu','Hegde','Kamat','Lal'
    ])[g] AS last_name,
    (ARRAY['MALE','FEMALE','MALE','FEMALE','MALE'])[((g - 1) % 5) + 1] AS gender,
    (ARRAY[
      'General Medicine','Cardiology','Orthopedics','Pediatrics','Dermatology',
      'Gynecology','Neurology','ENT','Ophthalmology','Psychiatry'
    ])[((g - 1) % 10) + 1] AS specialization,
    (ARRAY[
      'Medicine','Cardiology','Orthopedics','Pediatrics','Dermatology',
      'OBG','Neurology','ENT','Eye','Mental Health'
    ])[((g - 1) % 10) + 1] AS department,
    (ARRAY['MBBS','MD','MS','DNB','MBBS, MD'])[((g - 1) % 5) + 1] AS qualification,
    400 + ((g - 1) % 12) * 50 AS fee
  FROM generate_series(1, 50) AS g
),
mapped AS (
  SELECT
    s.*,
    h.ids[(((s.n - 1) % array_length(h.ids, 1)) + 1)] AS hospital_id,
    h.names[(((s.n - 1) % array_length(h.names, 1)) + 1)] AS hospital_name
  FROM seed s
  CROSS JOIN hosp_arr h
)
INSERT INTO svc.doctors (
  doctor_id, first_name, last_name, gender, specialization, department, qualification,
  email, mobile_number, hospital_id, hospital_name, clinic_name, consultation_fee, status
)
SELECT
  doctor_id,
  first_name,
  last_name,
  gender,
  specialization,
  department,
  qualification,
  lower(first_name) || '.' || lower(last_name) || '@medtrack.example',
  '98' || lpad((20000000 + n)::text, 8, '0'),
  hospital_id,
  hospital_name,
  hospital_name || ' OPD',
  fee,
  'ACTIVE'
FROM mapped
ON CONFLICT (doctor_id) DO UPDATE SET
  hospital_id = EXCLUDED.hospital_id,
  hospital_name = EXCLUDED.hospital_name,
  specialization = EXCLUDED.specialization,
  department = EXCLUDED.department,
  consultation_fee = EXCLUDED.consultation_fee,
  updated_at = NOW();

-- Mirror into app section tables used by booking UI
INSERT INTO svc.doctor_personal (
  doctor_id, first_name, last_name, gender, created_date, updated_date
)
SELECT doctor_id, first_name, last_name, gender, NOW(), NOW()
FROM svc.doctors
WHERE doctor_id LIKE 'DOC-SEED-%'
ON CONFLICT (doctor_id) DO UPDATE SET
  first_name = EXCLUDED.first_name,
  last_name = EXCLUDED.last_name,
  gender = EXCLUDED.gender,
  updated_date = NOW();

INSERT INTO svc.doctor_clinic (
  doctor_id, hospital_id, hospital_name, clinic_name, consultation_fee, consultation_type,
  available_days, available_time_slots
)
SELECT
  doctor_id,
  hospital_id,
  hospital_name,
  clinic_name,
  consultation_fee,
  'OPD',
  'Mon-Sat',
  '09:00-13:00,14:00-18:00'
FROM svc.doctors
WHERE doctor_id LIKE 'DOC-SEED-%'
ON CONFLICT (doctor_id) DO UPDATE SET
  hospital_id = EXCLUDED.hospital_id,
  hospital_name = EXCLUDED.hospital_name,
  clinic_name = EXCLUDED.clinic_name,
  consultation_fee = EXCLUDED.consultation_fee;

INSERT INTO svc.doctor_professional (
  doctor_id, specialization, department, qualification, years_of_experience, current_designation
)
SELECT
  doctor_id,
  specialization,
  department,
  qualification,
  3 + (abs(hashtext(doctor_id)) % 20),
  'Consultant'
FROM svc.doctors
WHERE doctor_id LIKE 'DOC-SEED-%'
ON CONFLICT (doctor_id) DO UPDATE SET
  specialization = EXCLUDED.specialization,
  department = EXCLUDED.department,
  qualification = EXCLUDED.qualification;

INSERT INTO svc.doctor_contact (
  doctor_id, email, mobile_number, city, country
)
SELECT
  d.doctor_id,
  d.email,
  d.mobile_number,
  h.city,
  COALESCE(h.country, 'India')
FROM svc.doctors d
LEFT JOIN svc.hospitals h ON h.id = d.hospital_id
WHERE d.doctor_id LIKE 'DOC-SEED-%'
ON CONFLICT (doctor_id) DO UPDATE SET
  email = EXCLUDED.email,
  mobile_number = EXCLUDED.mobile_number;

-- Summary
SELECT COUNT(*) AS doctors_master FROM svc.doctors;
SELECT hospital_id, COUNT(*) AS doctor_count
FROM svc.doctors
GROUP BY hospital_id
ORDER BY hospital_id;
SELECT d.doctor_id, d.first_name, d.last_name, d.hospital_id, d.hospital_name, d.specialization
FROM svc.doctors d
ORDER BY d.doctor_id
LIMIT 15;
