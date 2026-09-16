-- =============================================================================
-- MedTrack-APP — single PostgreSQL schema (schema: svc)
--
-- Order (required):
--   1) CREATE SCHEMA
--   2) CREATE ALL TABLES  (primary keys / unique / checks — NO foreign keys)
--   3) CREATE INDEXES
--   4) ADD FOREIGN KEYS   (only after every related table exists)
--
-- Usage:
--   psql -h localhost -U medtrack -d medtrackapp -f scripts/sql/medtrack-app-schema.sql
-- =============================================================================

SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

CREATE SCHEMA IF NOT EXISTS svc;
SET search_path TO svc, public;

-- ---------------------------------------------------------------------------
-- Helper: add a named FK only if it is not already present
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION svc.add_fk(p_name text, p_sql text)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = p_name) THEN
    EXECUTE p_sql;
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION svc.trg_hospitals_sync_hospital_id()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.hospital_id := NEW.id;
  RETURN NEW;
END;
$$;

-- =============================================================================
-- SECTION 1 — CREATE TABLES (no inter-table relations here)
-- =============================================================================

-- 1.1 Master / identity
CREATE TABLE IF NOT EXISTS svc.hospitals (
    id                 bigint NOT NULL,
    hospital_id        bigint NOT NULL,
    hospital_code      character varying(40) NOT NULL,
    hospital_name      character varying(255) NOT NULL,
    hospital_type      character varying(255),
    registration_number character varying(255),
    registration_json  text,
    email              character varying(255),
    primary_contact    character varying(255),
    city               character varying(255),
    state              character varying(255),
    country            character varying(255),
    status             character varying(255) NOT NULL,
    subscription_plan  character varying(255),
    admin_email        character varying(255),
    admin_password     character varying(255),
    created_at         timestamp(6) with time zone,
    updated_at         timestamp(6) with time zone,
    CONSTRAINT hospitals_pkey PRIMARY KEY (id),
    CONSTRAINT uk_hospitals_code UNIQUE (hospital_code),
    CONSTRAINT uk_hospitals_hospital_id UNIQUE (hospital_id)
);

CREATE TABLE IF NOT EXISTS svc.patients (
    id                 character varying(255) NOT NULL,
    name               character varying(255) NOT NULL,
    phone              character varying(32) NOT NULL,
    password           character varying(255) NOT NULL,
    email              character varying(255),
    age                integer,
    gender             character varying(255),
    address            character varying(255),
    blood_group        character varying(8),
    emergency_contact  character varying(32),
    created_at         timestamp(6) with time zone NOT NULL,
    updated_at         timestamp(6) with time zone,
    CONSTRAINT patients_pkey PRIMARY KEY (id),
    CONSTRAINT uk_patients_phone UNIQUE (phone)
);

CREATE TABLE IF NOT EXISTS svc.user_details (
    user_id            character varying(20) NOT NULL,
    user_name          character varying(100) NOT NULL,
    password           character varying(255) NOT NULL,
    phone              character varying(32),
    email              character varying(150),
    created_by         character varying(50),
    created_date       timestamp(6) with time zone NOT NULL,
    updated_by         character varying(50),
    updated_date       timestamp(6) with time zone,
    CONSTRAINT user_details_pkey PRIMARY KEY (user_id),
    CONSTRAINT uk_user_details_user_name UNIQUE (user_name),
    CONSTRAINT uk_user_details_phone UNIQUE (phone)
);

CREATE TABLE IF NOT EXISTS svc.login (
    id                 character varying(64) NOT NULL,
    login_type         character varying(20) NOT NULL,
    login_id           character varying(100) NOT NULL,
    password           character varying(255) NOT NULL,
    hospital_id        bigint,
    doctor_id          character varying(64),
    display_name       character varying(255),
    status             character varying(20) DEFAULT 'ACTIVE' NOT NULL,
    creation_date      timestamp with time zone DEFAULT now() NOT NULL,
    creation_user      character varying(100),
    update_date        timestamp with time zone,
    update_user        character varying(100),
    CONSTRAINT login_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.doctor_personal (
    doctor_id          character varying(40) NOT NULL,
    hospital_id        bigint NOT NULL,
    first_name         character varying(255),
    middle_name        character varying(255),
    last_name          character varying(255),
    gender             character varying(255),
    date_of_birth      date,
    blood_group        character varying(255),
    marital_status     character varying(255),
    profile_photo      text,
    login_password     character varying(255),
    created_date       timestamp(6) with time zone NOT NULL,
    updated_date       timestamp(6) with time zone,
    CONSTRAINT doctor_personal_pkey PRIMARY KEY (doctor_id)
);

CREATE TABLE IF NOT EXISTS svc.doctor_contact (
    doctor_id          character varying(40) NOT NULL,
    hospital_id        bigint NOT NULL,
    email              character varying(255),
    mobile_number      character varying(255),
    alternate_mobile_number character varying(255),
    emergency_contact_number character varying(255),
    residential_address text,
    city               character varying(255),
    state              character varying(255),
    country            character varying(255),
    postal_code        character varying(255),
    CONSTRAINT doctor_contact_pkey PRIMARY KEY (doctor_id)
);

CREATE TABLE IF NOT EXISTS svc.doctor_clinic (
    doctor_id          character varying(40) NOT NULL,
    hospital_id        bigint NOT NULL,
    hospital_name      character varying(255),
    clinic_name        character varying(255),
    branch             character varying(255),
    consultation_type  character varying(255),
    consultation_fee   double precision,
    follow_up_fee      double precision,
    available_days     character varying(255),
    available_time_slots character varying(255),
    CONSTRAINT doctor_clinic_pkey PRIMARY KEY (doctor_id)
);

CREATE TABLE IF NOT EXISTS svc.doctor_professional (
    doctor_id          character varying(40) NOT NULL,
    hospital_id        bigint NOT NULL,
    medical_registration_number character varying(255),
    medical_council_name character varying(255),
    registration_date  date,
    registration_valid_until date,
    years_of_experience integer,
    current_designation character varying(255),
    department         character varying(255),
    specialization     character varying(255),
    sub_specialization character varying(255),
    qualification      character varying(255),
    medical_college    character varying(255),
    graduation_year    integer,
    CONSTRAINT doctor_professional_pkey PRIMARY KEY (doctor_id)
);

CREATE TABLE IF NOT EXISTS svc.doctor_identity (
    doctor_id          character varying(40) NOT NULL,
    hospital_id        bigint NOT NULL,
    aadhaar_number     character varying(255),
    pan_number         character varying(255),
    passport_number    character varying(255),
    government_id_upload text,
    CONSTRAINT doctor_identity_pkey PRIMARY KEY (doctor_id)
);

CREATE TABLE IF NOT EXISTS svc.doctor_bank (
    doctor_id          character varying(40) NOT NULL,
    hospital_id        bigint NOT NULL,
    account_holder_name character varying(255),
    account_number     character varying(255),
    bank_name          character varying(255),
    ifsc_code          character varying(255),
    upi_id             character varying(255),
    CONSTRAINT doctor_bank_pkey PRIMARY KEY (doctor_id)
);

CREATE TABLE IF NOT EXISTS svc.doctor_documents (
    doctor_id          character varying(40) NOT NULL,
    hospital_id        bigint NOT NULL,
    identity_proof     text,
    address_proof      text,
    degree_certificate text,
    medical_registration_certificate text,
    experience_certificate text,
    passport_size_photograph text,
    digital_signature  text,
    CONSTRAINT doctor_documents_pkey PRIMARY KEY (doctor_id)
);

CREATE TABLE IF NOT EXISTS svc.doctors (
    doctor_id          character varying(40) NOT NULL,
    hospital_id        bigint NOT NULL,
    first_name         character varying(255) NOT NULL,
    last_name          character varying(255) NOT NULL,
    middle_name        character varying(255),
    gender             character varying(40),
    specialization     character varying(255),
    department         character varying(255),
    qualification      character varying(255),
    email              character varying(255),
    mobile_number      character varying(40),
    hospital_name      character varying(255),
    clinic_name        character varying(255),
    consultation_fee   double precision,
    status             character varying(40) DEFAULT 'ACTIVE' NOT NULL,
    created_at         timestamp with time zone DEFAULT now() NOT NULL,
    updated_at         timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT doctors_pkey PRIMARY KEY (doctor_id)
);

CREATE TABLE IF NOT EXISTS svc."user" (
    id                 character varying(255) NOT NULL,
    hospital_id        bigint,
    doctor_id          character varying(40) NOT NULL,
    name               character varying(255) NOT NULL,
    value              character varying(255) NOT NULL,
    flag               character varying(10) NOT NULL,
    description        text,
    user_creation      character varying(255),
    creation_date      timestamp(6) with time zone NOT NULL,
    update_user        character varying(255),
    update_date        timestamp(6) with time zone,
    CONSTRAINT user_pkey PRIMARY KEY (id)
);

-- 1.2 Clinic operations
CREATE TABLE IF NOT EXISTS svc.doctor_schedules (
    id                 character varying(255) NOT NULL,
    hospital_id        bigint NOT NULL,
    doctor_id          character varying(255),
    day_of_week        integer NOT NULL,
    start_time         character varying(255),
    end_time           character varying(255),
    slot_minutes       integer DEFAULT 15 NOT NULL,
    CONSTRAINT doctor_schedules_pkey PRIMARY KEY (id),
    CONSTRAINT uq_svc_doctor_schedules UNIQUE (doctor_id, day_of_week, start_time)
);

CREATE TABLE IF NOT EXISTS svc.doctor_availability (
    id                 character varying(255) NOT NULL,
    hospital_id        bigint NOT NULL,
    doctor_id          character varying(255),
    starts_at          timestamp with time zone,
    ends_at            timestamp with time zone,
    availability_type  character varying(255) DEFAULT 'AVAILABLE',
    reason             character varying(255),
    created_at         timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT doctor_availability_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.fee_rules (
    id                 character varying(255) NOT NULL,
    hospital_id        bigint NOT NULL,
    clinic_id          character varying(255),
    doctor_id          character varying(255),
    base_consult_fee   double precision DEFAULT 500 NOT NULL,
    fixed_consult_minutes integer DEFAULT 15 NOT NULL,
    overtime_fee_amount double precision DEFAULT 200 NOT NULL,
    overtime_fee_per_block_minutes integer DEFAULT 15 NOT NULL,
    currency           character varying(255) DEFAULT 'INR',
    CONSTRAINT fee_rules_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.bookings (
    id                 character varying(255) NOT NULL,
    hospital_id        bigint NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    patient_name       character varying(255) NOT NULL,
    patient_phone      character varying(255) NOT NULL,
    patient_age        integer,
    gender             character varying(255),
    address            character varying(255),
    reason             character varying(255),
    appointment_date   date NOT NULL,
    appointment_time   timestamp with time zone NOT NULL,
    token_number       integer,
    status             character varying(255) DEFAULT 'BOOKED' NOT NULL,
    created_at         timestamp with time zone DEFAULT now() NOT NULL,
    updated_at         timestamp with time zone,
    CONSTRAINT bookings_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.appointments (
    appointment_id     character varying(255) NOT NULL,
    hospital_id        bigint,
    doctor_id          character varying(64) NOT NULL,
    patient_id         character varying(64) NOT NULL,
    phone_number       character varying(32),
    patient_name       character varying(255) NOT NULL,
    patient_age        integer,
    gender             character varying(32),
    address            character varying(500),
    reason             character varying(1000),
    appointment_date   date NOT NULL,
    appointment_time   timestamp(6) with time zone NOT NULL,
    token_number       integer,
    status             character varying(32) NOT NULL,
    booked_by          character varying(32),
    booking_ref_id     character varying(36),
    created_by         character varying(50),
    created_date       timestamp(6) with time zone NOT NULL,
    updated_by         character varying(50),
    updated_date       timestamp(6) with time zone,
    CONSTRAINT appointments_pkey PRIMARY KEY (appointment_id)
);

CREATE TABLE IF NOT EXISTS svc.visits (
    id                 character varying(255) NOT NULL,
    hospital_id        bigint NOT NULL,
    clinic_id          character varying(255),
    patient_id         character varying(255),
    patient_name       character varying(255),
    patient_phone      character varying(255),
    patient_email      character varying(255),
    sms_consent        boolean DEFAULT true NOT NULL,
    email_consent      boolean DEFAULT true NOT NULL,
    doctor_id          character varying(255),
    doctor_name        character varying(255),
    status             character varying(255) DEFAULT 'BOOKED' NOT NULL,
    token_number       integer,
    reason             character varying(255),
    scheduled_start    timestamp with time zone,
    scheduled_end      timestamp with time zone,
    checked_in_at      timestamp with time zone,
    actual_start       timestamp with time zone,
    actual_end         timestamp with time zone,
    delay_minutes      integer DEFAULT 0 NOT NULL,
    base_fee           double precision,
    overtime_minutes   integer DEFAULT 0 NOT NULL,
    overtime_fee       double precision DEFAULT 0 NOT NULL,
    total_fee          double precision,
    fee_currency       character varying(255) DEFAULT 'INR',
    created_at         timestamp with time zone DEFAULT now() NOT NULL,
    updated_at         timestamp with time zone,
    CONSTRAINT visits_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.visit_events (
    id                 character varying(255) NOT NULL,
    hospital_id        bigint NOT NULL,
    visit_id           character varying(255),
    event_type         character varying(255),
    from_status        character varying(255),
    to_status          character varying(255),
    message            character varying(255),
    created_at         timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT visit_events_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.doctor_queue (
    id                 bigint GENERATED BY DEFAULT AS IDENTITY,
    hospital_id        bigint NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    token_no           integer NOT NULL,
    patient_id         character varying(64),
    patient_name       character varying(200),
    patient_phone      character varying(40),
    visit_id           character varying(64),
    status             character varying(20) DEFAULT 'WAITING' NOT NULL,
    checkin_time       timestamp with time zone,
    consultation_start timestamp with time zone,
    consultation_end   timestamp with time zone,
    queue_date         date NOT NULL,
    sms_two_ahead_sent boolean DEFAULT false NOT NULL,
    doctor_name        character varying(200),
    department         character varying(120),
    CONSTRAINT doctor_queue_pkey PRIMARY KEY (id),
    CONSTRAINT uq_doctor_queue_day_token UNIQUE (hospital_id, doctor_id, queue_date, token_no)
);

CREATE TABLE IF NOT EXISTS svc.notifications (
    id                 character varying(255) NOT NULL,
    hospital_id        bigint NOT NULL,
    clinic_id          character varying(255),
    visit_id           character varying(255),
    patient_id         character varying(255),
    event_code         character varying(255),
    channel            character varying(255),
    recipient          character varying(255),
    subject            character varying(255),
    body               character varying(4000),
    status             character varying(255) DEFAULT 'PENDING',
    provider_ref       character varying(255),
    error_message      character varying(255),
    sent_at            timestamp with time zone,
    created_at         timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT notifications_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.patient_reports (
    id                 character varying(255) NOT NULL,
    hospital_id        bigint,
    doctor_id          character varying(64),
    doctor_name        character varying(255),
    patient_name       character varying(255),
    patient_phone      character varying(32) NOT NULL,
    visit_id           character varying(64),
    report_type        character varying(32) NOT NULL,
    title              character varying(500) NOT NULL,
    description        character varying(2000),
    file_url           character varying(1000),
    report_date        date,
    created_at         timestamp(6) with time zone NOT NULL,
    CONSTRAINT patient_reports_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.documents (
    id                 character varying(64) NOT NULL,
    hospital_id        bigint NOT NULL,
    patient_name       character varying(255) NOT NULL,
    aadhaar_number     character varying(20),
    phone_number       character varying(32) NOT NULL,
    document_type      character varying(64),
    file_path          character varying(1000),
    destination_path   character varying(1000),
    source_path        character varying(2000),
    file_upload_1      character varying(1000),
    file_upload_2      character varying(1000),
    file_upload_3      character varying(1000),
    file_upload_4      character varying(1000),
    file_upload_5      character varying(1000),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100),
    update_date        timestamp(6) with time zone,
    update_user        character varying(100),
    CONSTRAINT documents_pkey PRIMARY KEY (id)
);

-- 1.3 HRM
CREATE TABLE IF NOT EXISTS svc.hrm_departments (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    name               character varying(120) NOT NULL,
    department_head    character varying(120),
    description        character varying(500),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_departments_pkey PRIMARY KEY (id),
    CONSTRAINT uk_hrm_dept_name_doctor UNIQUE (doctor_id, name)
);

CREATE TABLE IF NOT EXISTS svc.hrm_designations (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    department_id      character varying(64),
    department_name    character varying(120),
    name               character varying(120) NOT NULL,
    salary_grade       character varying(40),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_designations_pkey PRIMARY KEY (id),
    CONSTRAINT uk_hrm_desig_name_doctor UNIQUE (doctor_id, name)
);

CREATE TABLE IF NOT EXISTS svc.hrm_shifts (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    name               character varying(80) NOT NULL,
    start_time         time(6) without time zone NOT NULL,
    end_time           time(6) without time zone NOT NULL,
    break_duration_minutes integer,
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_shifts_pkey PRIMARY KEY (id),
    CONSTRAINT uk_hrm_shift_name_doctor UNIQUE (doctor_id, name)
);

CREATE TABLE IF NOT EXISTS svc.hrm_leave_types (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    name               character varying(80) NOT NULL,
    annual_quota       integer,
    description        character varying(300),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_leave_types_pkey PRIMARY KEY (id),
    CONSTRAINT uk_hrm_leave_type_doctor UNIQUE (doctor_id, name)
);

CREATE TABLE IF NOT EXISTS svc.hrm_employees (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40) NOT NULL,
    first_name         character varying(80) NOT NULL,
    last_name          character varying(80) NOT NULL,
    employee_type      character varying(40) NOT NULL,
    employment_status  character varying(20) NOT NULL,
    email              character varying(120),
    mobile_number      character varying(20),
    alternate_number   character varying(20),
    gender             character varying(20),
    date_of_birth      date,
    blood_group        character varying(10),
    marital_status     character varying(30),
    address            character varying(300),
    city               character varying(80),
    state              character varying(80),
    pin_code           character varying(20),
    photo              character varying(500),
    department         character varying(120),
    department_id      character varying(64),
    designation        character varying(120),
    designation_id     character varying(64),
    shift              character varying(80),
    shift_id           character varying(64),
    reporting_manager  character varying(120),
    joining_date       date,
    doctor_link_id     character varying(64),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_employees_pkey PRIMARY KEY (id),
    CONSTRAINT uk_hrm_employee_code UNIQUE (employee_id),
    CONSTRAINT uk_hrm_employee_doctor_link UNIQUE (doctor_link_id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_employee_documents (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40) NOT NULL,
    employee_pk        character varying(64) NOT NULL,
    document_type      character varying(60) NOT NULL,
    file_name          character varying(200),
    file_ref           character varying(500),
    notes              character varying(300),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_employee_documents_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_attendance (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40) NOT NULL,
    employee_pk        character varying(64) NOT NULL,
    employee_name      character varying(160),
    attendance_date    date NOT NULL,
    in_time            time(6) without time zone,
    out_time           time(6) without time zone,
    total_hours        numeric(6,2),
    status             character varying(20) NOT NULL,
    source             character varying(40),
    correction_requested boolean,
    correction_reason  character varying(400),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_attendance_pkey PRIMARY KEY (id),
    CONSTRAINT uk_hrm_att_emp_date UNIQUE (employee_id, attendance_date, doctor_id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_leave_balances (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40) NOT NULL,
    employee_pk        character varying(64) NOT NULL,
    leave_type_id      character varying(64) NOT NULL,
    leave_type         character varying(80),
    total_leave        integer,
    used_leave         integer,
    remaining_leave    integer,
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_leave_balances_pkey PRIMARY KEY (id),
    CONSTRAINT uk_hrm_leave_bal UNIQUE (employee_id, leave_type_id, doctor_id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_leave_requests (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40) NOT NULL,
    employee_pk        character varying(64) NOT NULL,
    employee_name      character varying(160),
    leave_type_id      character varying(64) NOT NULL,
    leave_type         character varying(80),
    from_date          date NOT NULL,
    to_date            date NOT NULL,
    reason             character varying(500),
    status             character varying(20) NOT NULL,
    attachment         character varying(500),
    approver_remarks   character varying(400),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_leave_requests_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_holidays (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    holiday_date       date NOT NULL,
    name               character varying(120) NOT NULL,
    description        character varying(400),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_holidays_pkey PRIMARY KEY (id),
    CONSTRAINT uk_hrm_holiday_date_doctor UNIQUE (doctor_id, holiday_date)
);

CREATE TABLE IF NOT EXISTS svc.hrm_candidates (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    name               character varying(160) NOT NULL,
    email              character varying(120),
    mobile             character varying(20),
    applied_position   character varying(120),
    resume             character varying(500),
    status             character varying(40),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_candidates_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_interviews (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    candidate_id       character varying(64) NOT NULL,
    candidate_name     character varying(160),
    interview_date     date NOT NULL,
    interviewer        character varying(120),
    remarks            character varying(500),
    status             character varying(20) NOT NULL,
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_interviews_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_onboarding (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40) NOT NULL,
    employee_pk        character varying(64) NOT NULL,
    employee_name      character varying(160),
    document_verification boolean,
    email_created      boolean,
    department_assigned boolean,
    shift_assigned     boolean,
    salary_assigned    boolean,
    id_card_generated  boolean,
    status             character varying(40),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_onboarding_pkey PRIMARY KEY (id),
    CONSTRAINT uk_hrm_onboard_emp UNIQUE (employee_id, doctor_id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_salary_structures (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40) NOT NULL,
    employee_pk        character varying(64) NOT NULL,
    employee_name      character varying(160),
    basic_salary       numeric(12,2) NOT NULL,
    hra                numeric(12,2),
    da                 numeric(12,2),
    conveyance         numeric(12,2),
    medical_allowance  numeric(12,2),
    special_allowance  numeric(12,2),
    pf                 numeric(12,2),
    esi                numeric(12,2),
    professional_tax   numeric(12,2),
    income_tax         numeric(12,2),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_salary_structures_pkey PRIMARY KEY (id),
    CONSTRAINT uk_hrm_salary_emp UNIQUE (employee_id, doctor_id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_payslips (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40) NOT NULL,
    employee_pk        character varying(64) NOT NULL,
    employee_name      character varying(160),
    pay_month          integer NOT NULL,
    pay_year           integer NOT NULL,
    working_days       integer,
    present_days       integer,
    absent_days        integer,
    earnings_json      character varying(2000),
    deductions_json    character varying(2000),
    gross_earnings     numeric(12,2),
    leave_deduction    numeric(12,2),
    total_deductions   numeric(12,2),
    net_salary         numeric(12,2),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_payslips_pkey PRIMARY KEY (id),
    CONSTRAINT uk_hrm_payslip UNIQUE (employee_id, pay_month, pay_year, doctor_id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_performance_reviews (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40) NOT NULL,
    employee_pk        character varying(64) NOT NULL,
    employee_name      character varying(160),
    review_date        date NOT NULL,
    rating             integer NOT NULL,
    strengths          character varying(1000),
    improvements       character varying(1000),
    manager_remarks    character varying(1000),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_performance_reviews_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_resignations (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40) NOT NULL,
    employee_pk        character varying(64) NOT NULL,
    employee_name      character varying(160),
    resignation_date   date NOT NULL,
    last_working_day   date,
    reason             character varying(500),
    status             character varying(40),
    asset_return       boolean,
    clearance          boolean,
    experience_letter  boolean,
    relieving_letter   boolean,
    final_settlement   boolean,
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_resignations_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_assets (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40),
    employee_name      character varying(160),
    employee_pk        character varying(64),
    asset_type         character varying(60) NOT NULL,
    asset_name         character varying(160),
    serial_number      character varying(80),
    allocated_date     date,
    status             character varying(40),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_assets_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_trainings (
    id                 character varying(255) NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    name               character varying(160) NOT NULL,
    trainer            character varying(120),
    start_date         date,
    end_date           date,
    description        character varying(400),
    participants       character varying(1000),
    creation_date      timestamp(6) with time zone NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp(6) with time zone NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_trainings_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS svc.hrm_inbox (
    id                 character varying(255) NOT NULL,
    hospital_id        bigint NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40) NOT NULL,
    employee_name      character varying(160),
    category           character varying(40) NOT NULL,
    document_name      character varying(200) NOT NULL,
    title              character varying(240) NOT NULL,
    status             character varying(30) DEFAULT 'PENDING' NOT NULL,
    action_required    character varying(80) DEFAULT 'Acknowledgement Required' NOT NULL,
    message_body       character varying(1000),
    file_url           character varying(500),
    requested_by       character varying(120),
    requested_at       timestamp with time zone,
    acknowledged_at    timestamp with time zone,
    archived           boolean DEFAULT false NOT NULL,
    creation_date      timestamp with time zone DEFAULT now() NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp with time zone DEFAULT now() NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_inbox_pkey PRIMARY KEY (id),
    CONSTRAINT ck_hrm_inbox_category CHECK (category IN ('PERFORMANCE', 'LETTER_RELEASE', 'SALARY_INCREMENT', 'DOCUMENTS')),
    CONSTRAINT ck_hrm_inbox_status CHECK (status IN ('PENDING', 'ACKNOWLEDGED', 'DOWNLOADED', 'ARCHIVED'))
);

CREATE TABLE IF NOT EXISTS svc.hrm_wfh_requests (
    id                 character varying(255) NOT NULL,
    hospital_id        bigint NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40) NOT NULL,
    employee_name      character varying(160),
    from_date          date NOT NULL,
    to_date            date NOT NULL,
    days               numeric(6,2) DEFAULT 1 NOT NULL,
    note               character varying(1000),
    notify_to          character varying(500),
    status             character varying(20) DEFAULT 'Pending' NOT NULL,
    creation_date      timestamp with time zone DEFAULT now() NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp with time zone DEFAULT now() NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT hrm_wfh_requests_pkey PRIMARY KEY (id),
    CONSTRAINT ck_hrm_wfh_dates CHECK (to_date >= from_date),
    CONSTRAINT ck_hrm_wfh_status CHECK (status IN ('Pending', 'Approved', 'Rejected', 'Cancelled'))
);

CREATE TABLE IF NOT EXISTS svc.leave_applications (
    id                 character varying(255) NOT NULL,
    hospital_id        bigint NOT NULL,
    doctor_id          character varying(64) NOT NULL,
    employee_id        character varying(40) NOT NULL,
    employee_name      character varying(160),
    leave_type_id      character varying(64) NOT NULL,
    leave_type         character varying(80) NOT NULL,
    from_date          date NOT NULL,
    to_date            date NOT NULL,
    days               numeric(6,2) DEFAULT 1 NOT NULL,
    reason             character varying(500),
    status             character varying(20) DEFAULT 'Pending' NOT NULL,
    approver_remarks   character varying(400),
    creation_date      timestamp with time zone DEFAULT now() NOT NULL,
    creation_user      character varying(100) NOT NULL,
    update_date        timestamp with time zone DEFAULT now() NOT NULL,
    update_user        character varying(100) NOT NULL,
    CONSTRAINT leave_applications_pkey PRIMARY KEY (id),
    CONSTRAINT ck_leave_app_dates CHECK (to_date >= from_date),
    CONSTRAINT ck_leave_app_status CHECK (status IN ('Pending', 'Approved', 'Rejected', 'Cancelled'))
);

-- =============================================================================
-- SECTION 2 — INDEXES (after tables exist)
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_login_type_id ON svc.login (login_type, login_id);
CREATE INDEX IF NOT EXISTS idx_login_hospital ON svc.login (hospital_id);
CREATE INDEX IF NOT EXISTS idx_login_doctor ON svc.login (doctor_id);

CREATE INDEX IF NOT EXISTS idx_appointments_doctor ON svc.appointments (doctor_id);
CREATE INDEX IF NOT EXISTS idx_appointments_hospital ON svc.appointments (hospital_id);
CREATE INDEX IF NOT EXISTS idx_appointments_phone ON svc.appointments (phone_number);
CREATE INDEX IF NOT EXISTS idx_appointments_patient ON svc.appointments (patient_id);
CREATE INDEX IF NOT EXISTS idx_appointments_doctor_date ON svc.appointments (doctor_id, appointment_date);
CREATE INDEX IF NOT EXISTS idx_appointments_hospital_doctor ON svc.appointments (hospital_id, doctor_id);
CREATE INDEX IF NOT EXISTS idx_appointments_booking_ref ON svc.appointments (booking_ref_id);

CREATE INDEX IF NOT EXISTS idx_bookings_hospital ON svc.bookings (hospital_id);
CREATE INDEX IF NOT EXISTS idx_bookings_doctor_date ON svc.bookings (doctor_id, appointment_date);

CREATE INDEX IF NOT EXISTS idx_visits_hospital ON svc.visits (hospital_id);
CREATE INDEX IF NOT EXISTS idx_visits_doctor ON svc.visits (doctor_id);
CREATE INDEX IF NOT EXISTS idx_visit_events_visit ON svc.visit_events (visit_id);

CREATE INDEX IF NOT EXISTS idx_doctor_queue_hospital_doctor ON svc.doctor_queue (hospital_id, doctor_id, queue_date);
CREATE INDEX IF NOT EXISTS idx_documents_hospital_phone ON svc.documents (hospital_id, phone_number);
CREATE INDEX IF NOT EXISTS idx_documents_aadhaar ON svc.documents (aadhaar_number);

-- =============================================================================
-- SECTION 3 — FOREIGN KEYS (run only after every table above exists)
--
-- Relationship map:
--   hospitals(id)              <- hospital_id on clinical / doctor / login / docs / HRM
--   doctor_personal(doctor_id) <- doctor profile slices, login.doctor_id, "user".doctor_id
--   bookings(id)               <- appointments.booking_ref_id
--   visits(id)                 <- visit_events.visit_id, doctor_queue.visit_id
--   hrm_departments / designations / shifts / leave_types / employees / candidates
-- =============================================================================

-- 3.1 Child → hospitals
SELECT svc.add_fk('fk_login_hospital',
  'ALTER TABLE svc.login ADD CONSTRAINT fk_login_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_doctor_personal_hospital',
  'ALTER TABLE svc.doctor_personal ADD CONSTRAINT fk_doctor_personal_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_doctor_contact_hospital',
  'ALTER TABLE svc.doctor_contact ADD CONSTRAINT fk_doctor_contact_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_doctor_clinic_hospital',
  'ALTER TABLE svc.doctor_clinic ADD CONSTRAINT fk_doctor_clinic_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_doctor_professional_hospital',
  'ALTER TABLE svc.doctor_professional ADD CONSTRAINT fk_doctor_professional_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_doctor_identity_hospital',
  'ALTER TABLE svc.doctor_identity ADD CONSTRAINT fk_doctor_identity_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_doctor_bank_hospital',
  'ALTER TABLE svc.doctor_bank ADD CONSTRAINT fk_doctor_bank_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_doctor_documents_hospital',
  'ALTER TABLE svc.doctor_documents ADD CONSTRAINT fk_doctor_documents_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_doctors_hospital',
  'ALTER TABLE svc.doctors ADD CONSTRAINT fk_doctors_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_user_hospital',
  'ALTER TABLE svc."user" ADD CONSTRAINT fk_user_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_doctor_schedules_hospital',
  'ALTER TABLE svc.doctor_schedules ADD CONSTRAINT fk_doctor_schedules_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_doctor_availability_hospital',
  'ALTER TABLE svc.doctor_availability ADD CONSTRAINT fk_doctor_availability_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_fee_rules_hospital',
  'ALTER TABLE svc.fee_rules ADD CONSTRAINT fk_fee_rules_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_bookings_hospital',
  'ALTER TABLE svc.bookings ADD CONSTRAINT fk_bookings_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_appointments_hospital',
  'ALTER TABLE svc.appointments ADD CONSTRAINT fk_appointments_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_visits_hospital',
  'ALTER TABLE svc.visits ADD CONSTRAINT fk_visits_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_visit_events_hospital',
  'ALTER TABLE svc.visit_events ADD CONSTRAINT fk_visit_events_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_doctor_queue_hospital',
  'ALTER TABLE svc.doctor_queue ADD CONSTRAINT fk_doctor_queue_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_notifications_hospital',
  'ALTER TABLE svc.notifications ADD CONSTRAINT fk_notifications_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_patient_reports_hospital',
  'ALTER TABLE svc.patient_reports ADD CONSTRAINT fk_patient_reports_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_documents_hospital',
  'ALTER TABLE svc.documents ADD CONSTRAINT fk_documents_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_hrm_inbox_hospital',
  'ALTER TABLE svc.hrm_inbox ADD CONSTRAINT fk_hrm_inbox_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_hrm_wfh_hospital',
  'ALTER TABLE svc.hrm_wfh_requests ADD CONSTRAINT fk_hrm_wfh_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');
SELECT svc.add_fk('fk_leave_app_hospital',
  'ALTER TABLE svc.leave_applications ADD CONSTRAINT fk_leave_app_hospital FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id)');

-- 3.2 Doctor profile slices → doctor_personal (after doctor_personal exists)
SELECT svc.add_fk('fk_doctor_contact_personal',
  'ALTER TABLE svc.doctor_contact ADD CONSTRAINT fk_doctor_contact_personal FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id)');
SELECT svc.add_fk('fk_doctor_clinic_personal',
  'ALTER TABLE svc.doctor_clinic ADD CONSTRAINT fk_doctor_clinic_personal FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id)');
SELECT svc.add_fk('fk_doctor_professional_personal',
  'ALTER TABLE svc.doctor_professional ADD CONSTRAINT fk_doctor_professional_personal FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id)');
SELECT svc.add_fk('fk_doctor_identity_personal',
  'ALTER TABLE svc.doctor_identity ADD CONSTRAINT fk_doctor_identity_personal FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id)');
SELECT svc.add_fk('fk_doctor_bank_personal',
  'ALTER TABLE svc.doctor_bank ADD CONSTRAINT fk_doctor_bank_personal FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id)');
SELECT svc.add_fk('fk_doctor_documents_personal',
  'ALTER TABLE svc.doctor_documents ADD CONSTRAINT fk_doctor_documents_personal FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id)');
SELECT svc.add_fk('fk_user_doctor',
  'ALTER TABLE svc."user" ADD CONSTRAINT fk_user_doctor FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id)');
SELECT svc.add_fk('fk_login_doctor',
  'ALTER TABLE svc.login ADD CONSTRAINT fk_login_doctor FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id)');

-- 3.3 Booking / visit chain (parents created in section 1)
SELECT svc.add_fk('fk_appointments_booking',
  'ALTER TABLE svc.appointments ADD CONSTRAINT fk_appointments_booking FOREIGN KEY (booking_ref_id) REFERENCES svc.bookings(id)');
SELECT svc.add_fk('fk_visit_events_visit',
  'ALTER TABLE svc.visit_events ADD CONSTRAINT fk_visit_events_visit FOREIGN KEY (visit_id) REFERENCES svc.visits(id)');
SELECT svc.add_fk('fk_doctor_queue_visit',
  'ALTER TABLE svc.doctor_queue ADD CONSTRAINT fk_doctor_queue_visit FOREIGN KEY (visit_id) REFERENCES svc.visits(id)');
SELECT svc.add_fk('fk_notifications_visit',
  'ALTER TABLE svc.notifications ADD CONSTRAINT fk_notifications_visit FOREIGN KEY (visit_id) REFERENCES svc.visits(id)');

-- 3.4 HRM (masters first, then children)
SELECT svc.add_fk('fk_hrm_designations_department',
  'ALTER TABLE svc.hrm_designations ADD CONSTRAINT fk_hrm_designations_department FOREIGN KEY (department_id) REFERENCES svc.hrm_departments(id)');
SELECT svc.add_fk('fk_hrm_employees_department',
  'ALTER TABLE svc.hrm_employees ADD CONSTRAINT fk_hrm_employees_department FOREIGN KEY (department_id) REFERENCES svc.hrm_departments(id)');
SELECT svc.add_fk('fk_hrm_employees_designation',
  'ALTER TABLE svc.hrm_employees ADD CONSTRAINT fk_hrm_employees_designation FOREIGN KEY (designation_id) REFERENCES svc.hrm_designations(id)');
SELECT svc.add_fk('fk_hrm_employees_shift',
  'ALTER TABLE svc.hrm_employees ADD CONSTRAINT fk_hrm_employees_shift FOREIGN KEY (shift_id) REFERENCES svc.hrm_shifts(id)');
SELECT svc.add_fk('fk_hrm_interviews_candidate',
  'ALTER TABLE svc.hrm_interviews ADD CONSTRAINT fk_hrm_interviews_candidate FOREIGN KEY (candidate_id) REFERENCES svc.hrm_candidates(id)');
SELECT svc.add_fk('fk_hrm_emp_docs_employee',
  'ALTER TABLE svc.hrm_employee_documents ADD CONSTRAINT fk_hrm_emp_docs_employee FOREIGN KEY (employee_pk) REFERENCES svc.hrm_employees(id)');
SELECT svc.add_fk('fk_hrm_attendance_employee',
  'ALTER TABLE svc.hrm_attendance ADD CONSTRAINT fk_hrm_attendance_employee FOREIGN KEY (employee_pk) REFERENCES svc.hrm_employees(id)');
SELECT svc.add_fk('fk_hrm_leave_bal_employee',
  'ALTER TABLE svc.hrm_leave_balances ADD CONSTRAINT fk_hrm_leave_bal_employee FOREIGN KEY (employee_pk) REFERENCES svc.hrm_employees(id)');
SELECT svc.add_fk('fk_hrm_leave_bal_type',
  'ALTER TABLE svc.hrm_leave_balances ADD CONSTRAINT fk_hrm_leave_bal_type FOREIGN KEY (leave_type_id) REFERENCES svc.hrm_leave_types(id)');
SELECT svc.add_fk('fk_hrm_leave_req_employee',
  'ALTER TABLE svc.hrm_leave_requests ADD CONSTRAINT fk_hrm_leave_req_employee FOREIGN KEY (employee_pk) REFERENCES svc.hrm_employees(id)');
SELECT svc.add_fk('fk_hrm_leave_req_type',
  'ALTER TABLE svc.hrm_leave_requests ADD CONSTRAINT fk_hrm_leave_req_type FOREIGN KEY (leave_type_id) REFERENCES svc.hrm_leave_types(id)');
SELECT svc.add_fk('fk_leave_app_type',
  'ALTER TABLE svc.leave_applications ADD CONSTRAINT fk_leave_app_type FOREIGN KEY (leave_type_id) REFERENCES svc.hrm_leave_types(id)');

-- =============================================================================
-- SECTION 4 — TRIGGERS
-- =============================================================================

DROP TRIGGER IF EXISTS hospitals_sync_hospital_id ON svc.hospitals;
CREATE TRIGGER hospitals_sync_hospital_id
  BEFORE INSERT OR UPDATE OF id ON svc.hospitals
  FOR EACH ROW
  EXECUTE FUNCTION svc.trg_hospitals_sync_hospital_id();

-- End of MedTrack-APP schema
