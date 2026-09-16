-- =============================================================================
-- MedTrack-APP — PostgreSQL schema (svc)
-- Order:
--   1) CREATE SCHEMA
--   2) CREATE ALL TABLES (no foreign keys)
--   3) PRIMARY KEYS / UNIQUE / INDEXES
--   4) FOREIGN KEYS (added AFTER all tables exist — avoids relation errors)
-- Usage:
--   psql -h localhost -U medtrack -d medtrackapp -f scripts/sql/schema.sql
-- =============================================================================

SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

CREATE SCHEMA IF NOT EXISTS svc;
SET search_path TO svc, public;

-- ---------------------------------------------------------------------------
-- Helper functions
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION svc.trg_hospitals_sync_hospital_id() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.hospital_id := NEW.id;
  RETURN NEW;
END;
$$;

-- ===========================================================================
-- SECTION 1: CREATE TABLES (parents first; NO foreign keys here)
-- ===========================================================================

-- svc.hospitals
CREATE TABLE IF NOT EXISTS svc.hospitals (
    id bigint NOT NULL,
    city character varying(255),
    country character varying(255),
    created_at timestamp(6) with time zone,
    email character varying(255),
    hospital_code character varying(40) NOT NULL,
    hospital_name character varying(255) NOT NULL,
    hospital_type character varying(255),
    primary_contact character varying(255),
    registration_json text,
    registration_number character varying(255),
    state character varying(255),
    status character varying(255) NOT NULL,
    subscription_plan character varying(255),
    updated_at timestamp(6) with time zone,
    admin_email character varying(255),
    admin_password character varying(255),
    hospital_id bigint NOT NULL
);

-- svc.patients
CREATE TABLE IF NOT EXISTS svc.patients (
    id character varying(255) NOT NULL,
    address character varying(255),
    age integer,
    blood_group character varying(8),
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(255),
    emergency_contact character varying(32),
    gender character varying(255),
    name character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    phone character varying(32) NOT NULL,
    updated_at timestamp(6) with time zone
);

-- svc.user_details
CREATE TABLE IF NOT EXISTS svc.user_details (
    user_id character varying(20) NOT NULL,
    created_by character varying(50),
    created_date timestamp(6) with time zone NOT NULL,
    password character varying(255) NOT NULL,
    updated_by character varying(50),
    updated_date timestamp(6) with time zone,
    user_name character varying(100) NOT NULL,
    phone character varying(32),
    email character varying(150)
);

-- svc.login
CREATE TABLE IF NOT EXISTS svc.login (
    id character varying(64) NOT NULL,
    login_type character varying(20) NOT NULL,
    login_id character varying(100) NOT NULL,
    password character varying(255) NOT NULL,
    hospital_id bigint,
    doctor_id character varying(64),
    display_name character varying(255),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    creation_date timestamp with time zone DEFAULT now() NOT NULL,
    creation_user character varying(100),
    update_date timestamp with time zone,
    update_user character varying(100)
);

-- svc.doctor_personal
CREATE TABLE IF NOT EXISTS svc.doctor_personal (
    doctor_id character varying(40) NOT NULL,
    blood_group character varying(255),
    created_date timestamp(6) with time zone NOT NULL,
    date_of_birth date,
    first_name character varying(255),
    gender character varying(255),
    last_name character varying(255),
    marital_status character varying(255),
    middle_name character varying(255),
    profile_photo text,
    updated_date timestamp(6) with time zone,
    login_password character varying(255),
    hospital_id bigint NOT NULL
);

-- svc.doctor_contact
CREATE TABLE IF NOT EXISTS svc.doctor_contact (
    doctor_id character varying(40) NOT NULL,
    alternate_mobile_number character varying(255),
    city character varying(255),
    country character varying(255),
    email character varying(255),
    emergency_contact_number character varying(255),
    mobile_number character varying(255),
    postal_code character varying(255),
    residential_address text,
    state character varying(255),
    hospital_id bigint NOT NULL
);

-- svc.doctor_clinic
CREATE TABLE IF NOT EXISTS svc.doctor_clinic (
    doctor_id character varying(40) NOT NULL,
    available_days character varying(255),
    available_time_slots character varying(255),
    branch character varying(255),
    clinic_name character varying(255),
    consultation_fee double precision,
    consultation_type character varying(255),
    follow_up_fee double precision,
    hospital_id bigint NOT NULL,
    hospital_name character varying(255)
);

-- svc.doctor_professional
CREATE TABLE IF NOT EXISTS svc.doctor_professional (
    doctor_id character varying(40) NOT NULL,
    current_designation character varying(255),
    department character varying(255),
    graduation_year integer,
    medical_college character varying(255),
    medical_council_name character varying(255),
    medical_registration_number character varying(255),
    qualification character varying(255),
    registration_date date,
    registration_valid_until date,
    specialization character varying(255),
    sub_specialization character varying(255),
    years_of_experience integer,
    hospital_id bigint NOT NULL
);

-- svc.doctor_identity
CREATE TABLE IF NOT EXISTS svc.doctor_identity (
    doctor_id character varying(40) NOT NULL,
    aadhaar_number character varying(255),
    government_id_upload text,
    pan_number character varying(255),
    passport_number character varying(255),
    hospital_id bigint NOT NULL
);

-- svc.doctor_bank
CREATE TABLE IF NOT EXISTS svc.doctor_bank (
    doctor_id character varying(40) NOT NULL,
    account_holder_name character varying(255),
    account_number character varying(255),
    bank_name character varying(255),
    ifsc_code character varying(255),
    upi_id character varying(255),
    hospital_id bigint NOT NULL
);

-- svc.doctor_documents
CREATE TABLE IF NOT EXISTS svc.doctor_documents (
    doctor_id character varying(40) NOT NULL,
    address_proof text,
    degree_certificate text,
    digital_signature text,
    experience_certificate text,
    identity_proof text,
    medical_registration_certificate text,
    passport_size_photograph text,
    hospital_id bigint NOT NULL
);

-- svc.doctors
CREATE TABLE IF NOT EXISTS svc.doctors (
    doctor_id character varying(40) NOT NULL,
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    middle_name character varying(255),
    gender character varying(40),
    specialization character varying(255),
    department character varying(255),
    qualification character varying(255),
    email character varying(255),
    mobile_number character varying(40),
    hospital_id bigint NOT NULL,
    hospital_name character varying(255),
    clinic_name character varying(255),
    consultation_fee double precision,
    status character varying(40) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

-- svc."user"
CREATE TABLE IF NOT EXISTS svc."user" (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    description text,
    flag character varying(10) NOT NULL,
    name character varying(255) NOT NULL,
    update_date timestamp(6) with time zone,
    update_user character varying(255),
    user_creation character varying(255),
    value character varying(255) NOT NULL,
    hospital_id bigint,
    doctor_id character varying(40) NOT NULL
);

-- svc.doctor_schedules
CREATE TABLE IF NOT EXISTS svc.doctor_schedules (
    id character varying(255) NOT NULL,
    doctor_id character varying(255),
    day_of_week integer NOT NULL,
    start_time character varying(255),
    end_time character varying(255),
    slot_minutes integer DEFAULT 15 NOT NULL,
    hospital_id bigint NOT NULL
);

-- svc.doctor_availability
CREATE TABLE IF NOT EXISTS svc.doctor_availability (
    id character varying(255) NOT NULL,
    doctor_id character varying(255),
    starts_at timestamp with time zone,
    ends_at timestamp with time zone,
    availability_type character varying(255) DEFAULT 'AVAILABLE'::character varying,
    reason character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    hospital_id bigint NOT NULL
);

-- svc.fee_rules
CREATE TABLE IF NOT EXISTS svc.fee_rules (
    id character varying(255) NOT NULL,
    clinic_id character varying(255),
    doctor_id character varying(255),
    base_consult_fee double precision DEFAULT 500 NOT NULL,
    fixed_consult_minutes integer DEFAULT 15 NOT NULL,
    overtime_fee_amount double precision DEFAULT 200 NOT NULL,
    overtime_fee_per_block_minutes integer DEFAULT 15 NOT NULL,
    currency character varying(255) DEFAULT 'INR'::character varying,
    hospital_id bigint NOT NULL
);

-- svc.bookings
CREATE TABLE IF NOT EXISTS svc.bookings (
    id character varying(255) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    patient_name character varying(255) NOT NULL,
    patient_phone character varying(255) NOT NULL,
    patient_age integer,
    gender character varying(255),
    address character varying(255),
    reason character varying(255),
    appointment_date date NOT NULL,
    appointment_time timestamp with time zone NOT NULL,
    token_number integer,
    status character varying(255) DEFAULT 'BOOKED'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    hospital_id bigint NOT NULL
);

-- svc.appointments
CREATE TABLE IF NOT EXISTS svc.appointments (
    appointment_id character varying(255) NOT NULL,
    address character varying(500),
    appointment_date date NOT NULL,
    appointment_time timestamp(6) with time zone NOT NULL,
    booked_by character varying(32),
    booking_ref_id character varying(36),
    created_by character varying(50),
    created_date timestamp(6) with time zone NOT NULL,
    doctor_id character varying(64) NOT NULL,
    gender character varying(32),
    hospital_id bigint,
    patient_age integer,
    patient_id character varying(64) NOT NULL,
    patient_name character varying(255) NOT NULL,
    reason character varying(1000),
    status character varying(32) NOT NULL,
    token_number integer,
    updated_by character varying(50),
    updated_date timestamp(6) with time zone,
    phone_number character varying(32)
);

-- svc.visits
CREATE TABLE IF NOT EXISTS svc.visits (
    id character varying(255) NOT NULL,
    clinic_id character varying(255),
    patient_id character varying(255),
    patient_name character varying(255),
    patient_phone character varying(255),
    patient_email character varying(255),
    sms_consent boolean DEFAULT true NOT NULL,
    email_consent boolean DEFAULT true NOT NULL,
    doctor_id character varying(255),
    doctor_name character varying(255),
    status character varying(255) DEFAULT 'BOOKED'::character varying NOT NULL,
    token_number integer,
    reason character varying(255),
    scheduled_start timestamp with time zone,
    scheduled_end timestamp with time zone,
    checked_in_at timestamp with time zone,
    actual_start timestamp with time zone,
    actual_end timestamp with time zone,
    delay_minutes integer DEFAULT 0 NOT NULL,
    base_fee double precision,
    overtime_minutes integer DEFAULT 0 NOT NULL,
    overtime_fee double precision DEFAULT 0 NOT NULL,
    total_fee double precision,
    fee_currency character varying(255) DEFAULT 'INR'::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    hospital_id bigint NOT NULL
);

-- svc.visit_events
CREATE TABLE IF NOT EXISTS svc.visit_events (
    id character varying(255) NOT NULL,
    visit_id character varying(255),
    event_type character varying(255),
    from_status character varying(255),
    to_status character varying(255),
    message character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    hospital_id bigint NOT NULL
);

-- svc.doctor_queue
CREATE TABLE IF NOT EXISTS svc.doctor_queue (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    doctor_id character varying(64) NOT NULL,
    token_no integer NOT NULL,
    patient_id character varying(64),
    patient_name character varying(200),
    patient_phone character varying(40),
    visit_id character varying(64),
    status character varying(20) DEFAULT 'WAITING'::character varying NOT NULL,
    checkin_time timestamp with time zone,
    consultation_start timestamp with time zone,
    consultation_end timestamp with time zone,
    queue_date date NOT NULL,
    sms_two_ahead_sent boolean DEFAULT false NOT NULL,
    doctor_name character varying(200),
    department character varying(120)
);

-- svc.notifications
CREATE TABLE IF NOT EXISTS svc.notifications (
    id character varying(255) NOT NULL,
    clinic_id character varying(255),
    visit_id character varying(255),
    patient_id character varying(255),
    event_code character varying(255),
    channel character varying(255),
    recipient character varying(255),
    subject character varying(255),
    body character varying(4000),
    status character varying(255) DEFAULT 'PENDING'::character varying,
    provider_ref character varying(255),
    error_message character varying(255),
    sent_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    hospital_id bigint NOT NULL
);

-- svc.patient_reports
CREATE TABLE IF NOT EXISTS svc.patient_reports (
    id character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(2000),
    doctor_id character varying(64),
    doctor_name character varying(255),
    file_url character varying(1000),
    hospital_id bigint,
    patient_name character varying(255),
    patient_phone character varying(32) NOT NULL,
    report_date date,
    report_type character varying(32) NOT NULL,
    title character varying(500) NOT NULL,
    visit_id character varying(64)
);

-- svc.documents
CREATE TABLE IF NOT EXISTS svc.documents (
    id character varying(64) NOT NULL,
    aadhaar_number character varying(20),
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100),
    destination_path character varying(1000),
    file_path character varying(1000),
    file_upload_1 character varying(1000),
    file_upload_2 character varying(1000),
    file_upload_3 character varying(1000),
    file_upload_4 character varying(1000),
    file_upload_5 character varying(1000),
    hospital_id bigint NOT NULL,
    patient_name character varying(255) NOT NULL,
    phone_number character varying(32) NOT NULL,
    source_path character varying(2000),
    update_date timestamp(6) with time zone,
    update_user character varying(100),
    document_type character varying(64)
);

-- svc.hrm_assets
CREATE TABLE IF NOT EXISTS svc.hrm_assets (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    allocated_date date,
    asset_name character varying(160),
    asset_type character varying(60) NOT NULL,
    employee_id character varying(40),
    employee_name character varying(160),
    employee_pk character varying(64),
    serial_number character varying(80),
    status character varying(40)
);

-- svc.hrm_attendance
CREATE TABLE IF NOT EXISTS svc.hrm_attendance (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    attendance_date date NOT NULL,
    correction_reason character varying(400),
    correction_requested boolean,
    employee_id character varying(40) NOT NULL,
    employee_name character varying(160),
    employee_pk character varying(64) NOT NULL,
    in_time time(6) without time zone,
    out_time time(6) without time zone,
    source character varying(40),
    status character varying(20) NOT NULL,
    total_hours numeric(6,2)
);

-- svc.hrm_candidates
CREATE TABLE IF NOT EXISTS svc.hrm_candidates (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    applied_position character varying(120),
    email character varying(120),
    mobile character varying(20),
    name character varying(160) NOT NULL,
    resume character varying(500),
    status character varying(40)
);

-- svc.hrm_departments
CREATE TABLE IF NOT EXISTS svc.hrm_departments (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    department_head character varying(120),
    description character varying(500),
    name character varying(120) NOT NULL
);

-- svc.hrm_designations
CREATE TABLE IF NOT EXISTS svc.hrm_designations (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    department_id character varying(64),
    department_name character varying(120),
    name character varying(120) NOT NULL,
    salary_grade character varying(40)
);

-- svc.hrm_employee_documents
CREATE TABLE IF NOT EXISTS svc.hrm_employee_documents (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    document_type character varying(60) NOT NULL,
    employee_id character varying(40) NOT NULL,
    employee_pk character varying(64) NOT NULL,
    file_name character varying(200),
    file_ref character varying(500),
    notes character varying(300)
);

-- svc.hrm_employees
CREATE TABLE IF NOT EXISTS svc.hrm_employees (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    address character varying(300),
    alternate_number character varying(20),
    blood_group character varying(10),
    city character varying(80),
    date_of_birth date,
    department character varying(120),
    department_id character varying(64),
    designation character varying(120),
    designation_id character varying(64),
    doctor_link_id character varying(64),
    email character varying(120),
    employee_id character varying(40) NOT NULL,
    employee_type character varying(40) NOT NULL,
    employment_status character varying(20) NOT NULL,
    first_name character varying(80) NOT NULL,
    gender character varying(20),
    joining_date date,
    last_name character varying(80) NOT NULL,
    marital_status character varying(30),
    mobile_number character varying(20),
    photo character varying(500),
    pin_code character varying(20),
    reporting_manager character varying(120),
    shift character varying(80),
    shift_id character varying(64),
    state character varying(80)
);

-- svc.hrm_holidays
CREATE TABLE IF NOT EXISTS svc.hrm_holidays (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    description character varying(400),
    holiday_date date NOT NULL,
    name character varying(120) NOT NULL
);

-- svc.hrm_inbox
CREATE TABLE IF NOT EXISTS svc.hrm_inbox (
    id character varying(255) NOT NULL,
    hospital_id bigint NOT NULL,
    doctor_id character varying(64) NOT NULL,
    employee_id character varying(40) NOT NULL,
    employee_name character varying(160),
    category character varying(40) NOT NULL,
    document_name character varying(200) NOT NULL,
    title character varying(240) NOT NULL,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    action_required character varying(80) DEFAULT 'Acknowledgement Required'::character varying NOT NULL,
    message_body character varying(1000),
    file_url character varying(500),
    requested_by character varying(120),
    requested_at timestamp with time zone,
    acknowledged_at timestamp with time zone,
    archived boolean DEFAULT false NOT NULL,
    creation_date timestamp with time zone DEFAULT now() NOT NULL,
    creation_user character varying(100) NOT NULL,
    update_date timestamp with time zone DEFAULT now() NOT NULL,
    update_user character varying(100) NOT NULL,
    CONSTRAINT ck_hrm_inbox_category CHECK (((category)::text = ANY ((ARRAY['PERFORMANCE'::character varying, 'LETTER_RELEASE'::character varying, 'SALARY_INCREMENT'::character varying, 'DOCUMENTS'::character varying])::text[]))),
    CONSTRAINT ck_hrm_inbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACKNOWLEDGED'::character varying, 'DOWNLOADED'::character varying, 'ARCHIVED'::character varying])::text[])))
);

-- svc.hrm_interviews
CREATE TABLE IF NOT EXISTS svc.hrm_interviews (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    candidate_id character varying(64) NOT NULL,
    candidate_name character varying(160),
    interview_date date NOT NULL,
    interviewer character varying(120),
    remarks character varying(500),
    status character varying(20) NOT NULL
);

-- svc.hrm_leave_balances
CREATE TABLE IF NOT EXISTS svc.hrm_leave_balances (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    employee_id character varying(40) NOT NULL,
    employee_pk character varying(64) NOT NULL,
    leave_type character varying(80),
    leave_type_id character varying(64) NOT NULL,
    remaining_leave integer,
    total_leave integer,
    used_leave integer
);

-- svc.hrm_leave_requests
CREATE TABLE IF NOT EXISTS svc.hrm_leave_requests (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    approver_remarks character varying(400),
    attachment character varying(500),
    employee_id character varying(40) NOT NULL,
    employee_name character varying(160),
    employee_pk character varying(64) NOT NULL,
    from_date date NOT NULL,
    leave_type character varying(80),
    leave_type_id character varying(64) NOT NULL,
    reason character varying(500),
    status character varying(20) NOT NULL,
    to_date date NOT NULL
);

-- svc.hrm_leave_types
CREATE TABLE IF NOT EXISTS svc.hrm_leave_types (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    annual_quota integer,
    description character varying(300),
    name character varying(80) NOT NULL
);

-- svc.hrm_onboarding
CREATE TABLE IF NOT EXISTS svc.hrm_onboarding (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    department_assigned boolean,
    document_verification boolean,
    email_created boolean,
    employee_id character varying(40) NOT NULL,
    employee_name character varying(160),
    employee_pk character varying(64) NOT NULL,
    id_card_generated boolean,
    salary_assigned boolean,
    shift_assigned boolean,
    status character varying(40)
);

-- svc.hrm_payslips
CREATE TABLE IF NOT EXISTS svc.hrm_payslips (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    absent_days integer,
    deductions_json character varying(2000),
    earnings_json character varying(2000),
    employee_id character varying(40) NOT NULL,
    employee_name character varying(160),
    employee_pk character varying(64) NOT NULL,
    gross_earnings numeric(12,2),
    leave_deduction numeric(12,2),
    net_salary numeric(12,2),
    pay_month integer NOT NULL,
    pay_year integer NOT NULL,
    present_days integer,
    total_deductions numeric(12,2),
    working_days integer
);

-- svc.hrm_performance_reviews
CREATE TABLE IF NOT EXISTS svc.hrm_performance_reviews (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    employee_id character varying(40) NOT NULL,
    employee_name character varying(160),
    employee_pk character varying(64) NOT NULL,
    improvements character varying(1000),
    manager_remarks character varying(1000),
    rating integer NOT NULL,
    review_date date NOT NULL,
    strengths character varying(1000)
);

-- svc.hrm_resignations
CREATE TABLE IF NOT EXISTS svc.hrm_resignations (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    asset_return boolean,
    clearance boolean,
    employee_id character varying(40) NOT NULL,
    employee_name character varying(160),
    employee_pk character varying(64) NOT NULL,
    experience_letter boolean,
    final_settlement boolean,
    last_working_day date,
    reason character varying(500),
    relieving_letter boolean,
    resignation_date date NOT NULL,
    status character varying(40)
);

-- svc.hrm_salary_structures
CREATE TABLE IF NOT EXISTS svc.hrm_salary_structures (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    basic_salary numeric(12,2) NOT NULL,
    conveyance numeric(12,2),
    da numeric(12,2),
    employee_id character varying(40) NOT NULL,
    employee_name character varying(160),
    employee_pk character varying(64) NOT NULL,
    esi numeric(12,2),
    hra numeric(12,2),
    income_tax numeric(12,2),
    medical_allowance numeric(12,2),
    pf numeric(12,2),
    professional_tax numeric(12,2),
    special_allowance numeric(12,2)
);

-- svc.hrm_shifts
CREATE TABLE IF NOT EXISTS svc.hrm_shifts (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    break_duration_minutes integer,
    end_time time(6) without time zone NOT NULL,
    name character varying(80) NOT NULL,
    start_time time(6) without time zone NOT NULL
);

-- svc.hrm_trainings
CREATE TABLE IF NOT EXISTS svc.hrm_trainings (
    id character varying(255) NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    creation_user character varying(100) NOT NULL,
    doctor_id character varying(64) NOT NULL,
    update_date timestamp(6) with time zone NOT NULL,
    update_user character varying(100) NOT NULL,
    description character varying(400),
    end_date date,
    name character varying(160) NOT NULL,
    participants character varying(1000),
    start_date date,
    trainer character varying(120)
);

-- svc.hrm_wfh_requests
CREATE TABLE IF NOT EXISTS svc.hrm_wfh_requests (
    id character varying(255) NOT NULL,
    hospital_id bigint NOT NULL,
    doctor_id character varying(64) NOT NULL,
    employee_id character varying(40) NOT NULL,
    employee_name character varying(160),
    from_date date NOT NULL,
    to_date date NOT NULL,
    days numeric(6,2) DEFAULT 1 NOT NULL,
    note character varying(1000),
    notify_to character varying(500),
    status character varying(20) DEFAULT 'Pending'::character varying NOT NULL,
    creation_date timestamp with time zone DEFAULT now() NOT NULL,
    creation_user character varying(100) NOT NULL,
    update_date timestamp with time zone DEFAULT now() NOT NULL,
    update_user character varying(100) NOT NULL,
    CONSTRAINT ck_hrm_wfh_dates CHECK ((to_date >= from_date)),
    CONSTRAINT ck_hrm_wfh_status CHECK (((status)::text = ANY ((ARRAY['Pending'::character varying, 'Approved'::character varying, 'Rejected'::character varying, 'Cancelled'::character varying])::text[])))
);

-- svc.leave_applications
CREATE TABLE IF NOT EXISTS svc.leave_applications (
    id character varying(255) NOT NULL,
    hospital_id bigint NOT NULL,
    doctor_id character varying(64) NOT NULL,
    employee_id character varying(40) NOT NULL,
    employee_name character varying(160),
    leave_type_id character varying(64) NOT NULL,
    leave_type character varying(80) NOT NULL,
    from_date date NOT NULL,
    to_date date NOT NULL,
    days numeric(6,2) DEFAULT 1 NOT NULL,
    reason character varying(500),
    status character varying(20) DEFAULT 'Pending'::character varying NOT NULL,
    approver_remarks character varying(400),
    creation_date timestamp with time zone DEFAULT now() NOT NULL,
    creation_user character varying(100) NOT NULL,
    update_date timestamp with time zone DEFAULT now() NOT NULL,
    update_user character varying(100) NOT NULL,
    CONSTRAINT ck_leave_app_dates CHECK ((to_date >= from_date)),
    CONSTRAINT ck_leave_app_status CHECK (((status)::text = ANY ((ARRAY['Pending'::character varying, 'Approved'::character varying, 'Rejected'::character varying, 'Cancelled'::character varying])::text[])))
);

-- ===========================================================================
-- SECTION 2: PRIMARY KEYS, UNIQUE CONSTRAINTS, INDEXES
-- (Safe to re-run: skip if constraint/index already exists)
-- ===========================================================================

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'appointments_pkey') THEN
    ALTER TABLE ONLY svc.appointments
    ADD CONSTRAINT appointments_pkey PRIMARY KEY (appointment_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'bookings_pkey') THEN
    ALTER TABLE ONLY svc.bookings
    ADD CONSTRAINT bookings_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'doctor_availability_pkey') THEN
    ALTER TABLE ONLY svc.doctor_availability
    ADD CONSTRAINT doctor_availability_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'doctor_bank_pkey') THEN
    ALTER TABLE ONLY svc.doctor_bank
    ADD CONSTRAINT doctor_bank_pkey PRIMARY KEY (doctor_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'doctor_clinic_pkey') THEN
    ALTER TABLE ONLY svc.doctor_clinic
    ADD CONSTRAINT doctor_clinic_pkey PRIMARY KEY (doctor_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'doctor_contact_pkey') THEN
    ALTER TABLE ONLY svc.doctor_contact
    ADD CONSTRAINT doctor_contact_pkey PRIMARY KEY (doctor_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'doctor_documents_pkey') THEN
    ALTER TABLE ONLY svc.doctor_documents
    ADD CONSTRAINT doctor_documents_pkey PRIMARY KEY (doctor_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'doctor_identity_pkey') THEN
    ALTER TABLE ONLY svc.doctor_identity
    ADD CONSTRAINT doctor_identity_pkey PRIMARY KEY (doctor_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'doctor_personal_pkey') THEN
    ALTER TABLE ONLY svc.doctor_personal
    ADD CONSTRAINT doctor_personal_pkey PRIMARY KEY (doctor_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'doctor_professional_pkey') THEN
    ALTER TABLE ONLY svc.doctor_professional
    ADD CONSTRAINT doctor_professional_pkey PRIMARY KEY (doctor_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'doctor_queue_pkey') THEN
    ALTER TABLE ONLY svc.doctor_queue
    ADD CONSTRAINT doctor_queue_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'doctor_schedules_pkey') THEN
    ALTER TABLE ONLY svc.doctor_schedules
    ADD CONSTRAINT doctor_schedules_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'doctors_pkey') THEN
    ALTER TABLE ONLY svc.doctors
    ADD CONSTRAINT doctors_pkey PRIMARY KEY (doctor_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'documents_pkey') THEN
    ALTER TABLE ONLY svc.documents
    ADD CONSTRAINT documents_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fee_rules_pkey') THEN
    ALTER TABLE ONLY svc.fee_rules
    ADD CONSTRAINT fee_rules_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hospitals_pkey') THEN
    ALTER TABLE ONLY svc.hospitals
    ADD CONSTRAINT hospitals_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_assets_pkey') THEN
    ALTER TABLE ONLY svc.hrm_assets
    ADD CONSTRAINT hrm_assets_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_attendance_pkey') THEN
    ALTER TABLE ONLY svc.hrm_attendance
    ADD CONSTRAINT hrm_attendance_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_candidates_pkey') THEN
    ALTER TABLE ONLY svc.hrm_candidates
    ADD CONSTRAINT hrm_candidates_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_departments_pkey') THEN
    ALTER TABLE ONLY svc.hrm_departments
    ADD CONSTRAINT hrm_departments_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_designations_pkey') THEN
    ALTER TABLE ONLY svc.hrm_designations
    ADD CONSTRAINT hrm_designations_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_employee_documents_pkey') THEN
    ALTER TABLE ONLY svc.hrm_employee_documents
    ADD CONSTRAINT hrm_employee_documents_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_employees_pkey') THEN
    ALTER TABLE ONLY svc.hrm_employees
    ADD CONSTRAINT hrm_employees_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_holidays_pkey') THEN
    ALTER TABLE ONLY svc.hrm_holidays
    ADD CONSTRAINT hrm_holidays_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_inbox_pkey') THEN
    ALTER TABLE ONLY svc.hrm_inbox
    ADD CONSTRAINT hrm_inbox_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_interviews_pkey') THEN
    ALTER TABLE ONLY svc.hrm_interviews
    ADD CONSTRAINT hrm_interviews_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_leave_balances_pkey') THEN
    ALTER TABLE ONLY svc.hrm_leave_balances
    ADD CONSTRAINT hrm_leave_balances_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_leave_requests_pkey') THEN
    ALTER TABLE ONLY svc.hrm_leave_requests
    ADD CONSTRAINT hrm_leave_requests_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_leave_types_pkey') THEN
    ALTER TABLE ONLY svc.hrm_leave_types
    ADD CONSTRAINT hrm_leave_types_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_onboarding_pkey') THEN
    ALTER TABLE ONLY svc.hrm_onboarding
    ADD CONSTRAINT hrm_onboarding_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_payslips_pkey') THEN
    ALTER TABLE ONLY svc.hrm_payslips
    ADD CONSTRAINT hrm_payslips_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_performance_reviews_pkey') THEN
    ALTER TABLE ONLY svc.hrm_performance_reviews
    ADD CONSTRAINT hrm_performance_reviews_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_resignations_pkey') THEN
    ALTER TABLE ONLY svc.hrm_resignations
    ADD CONSTRAINT hrm_resignations_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_salary_structures_pkey') THEN
    ALTER TABLE ONLY svc.hrm_salary_structures
    ADD CONSTRAINT hrm_salary_structures_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_shifts_pkey') THEN
    ALTER TABLE ONLY svc.hrm_shifts
    ADD CONSTRAINT hrm_shifts_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_trainings_pkey') THEN
    ALTER TABLE ONLY svc.hrm_trainings
    ADD CONSTRAINT hrm_trainings_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'hrm_wfh_requests_pkey') THEN
    ALTER TABLE ONLY svc.hrm_wfh_requests
    ADD CONSTRAINT hrm_wfh_requests_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'leave_applications_pkey') THEN
    ALTER TABLE ONLY svc.leave_applications
    ADD CONSTRAINT leave_applications_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'login_pkey') THEN
    ALTER TABLE ONLY svc.login
    ADD CONSTRAINT login_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'notifications_pkey') THEN
    ALTER TABLE ONLY svc.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'patient_reports_pkey') THEN
    ALTER TABLE ONLY svc.patient_reports
    ADD CONSTRAINT patient_reports_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'patients_pkey') THEN
    ALTER TABLE ONLY svc.patients
    ADD CONSTRAINT patients_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk2ll1v15lrapsgaqdg5sq3ikev') THEN
    ALTER TABLE ONLY svc.doctor_schedules
    ADD CONSTRAINT uk2ll1v15lrapsgaqdg5sq3ikev UNIQUE (doctor_id, day_of_week, start_time);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk3losa44agqkfqpkxfdv7wf1dq') THEN
    ALTER TABLE ONLY svc.patients
    ADD CONSTRAINT uk3losa44agqkfqpkxfdv7wf1dq UNIQUE (phone);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk7pqjkt6mwigem3tve6e8j2qlp') THEN
    ALTER TABLE ONLY svc.user_details
    ADD CONSTRAINT uk7pqjkt6mwigem3tve6e8j2qlp UNIQUE (user_name);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hospitals_code') THEN
    ALTER TABLE ONLY svc.hospitals
    ADD CONSTRAINT uk_hospitals_code UNIQUE (hospital_code);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hospitals_hospital_id') THEN
    ALTER TABLE ONLY svc.hospitals
    ADD CONSTRAINT uk_hospitals_hospital_id UNIQUE (hospital_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hrm_att_emp_date') THEN
    ALTER TABLE ONLY svc.hrm_attendance
    ADD CONSTRAINT uk_hrm_att_emp_date UNIQUE (employee_id, attendance_date, doctor_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hrm_dept_name_doctor') THEN
    ALTER TABLE ONLY svc.hrm_departments
    ADD CONSTRAINT uk_hrm_dept_name_doctor UNIQUE (doctor_id, name);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hrm_desig_name_doctor') THEN
    ALTER TABLE ONLY svc.hrm_designations
    ADD CONSTRAINT uk_hrm_desig_name_doctor UNIQUE (doctor_id, name);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hrm_employee_code') THEN
    ALTER TABLE ONLY svc.hrm_employees
    ADD CONSTRAINT uk_hrm_employee_code UNIQUE (employee_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hrm_employee_doctor_link') THEN
    ALTER TABLE ONLY svc.hrm_employees
    ADD CONSTRAINT uk_hrm_employee_doctor_link UNIQUE (doctor_link_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hrm_holiday_date_doctor') THEN
    ALTER TABLE ONLY svc.hrm_holidays
    ADD CONSTRAINT uk_hrm_holiday_date_doctor UNIQUE (doctor_id, holiday_date);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hrm_leave_bal') THEN
    ALTER TABLE ONLY svc.hrm_leave_balances
    ADD CONSTRAINT uk_hrm_leave_bal UNIQUE (employee_id, leave_type_id, doctor_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hrm_leave_type_doctor') THEN
    ALTER TABLE ONLY svc.hrm_leave_types
    ADD CONSTRAINT uk_hrm_leave_type_doctor UNIQUE (doctor_id, name);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hrm_onboard_emp') THEN
    ALTER TABLE ONLY svc.hrm_onboarding
    ADD CONSTRAINT uk_hrm_onboard_emp UNIQUE (employee_id, doctor_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hrm_payslip') THEN
    ALTER TABLE ONLY svc.hrm_payslips
    ADD CONSTRAINT uk_hrm_payslip UNIQUE (employee_id, pay_month, pay_year, doctor_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hrm_salary_emp') THEN
    ALTER TABLE ONLY svc.hrm_salary_structures
    ADD CONSTRAINT uk_hrm_salary_emp UNIQUE (employee_id, doctor_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_hrm_shift_name_doctor') THEN
    ALTER TABLE ONLY svc.hrm_shifts
    ADD CONSTRAINT uk_hrm_shift_name_doctor UNIQUE (doctor_id, name);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ukhwi9icwrbompowjo1l8mqqu49') THEN
    ALTER TABLE ONLY svc.user_details
    ADD CONSTRAINT ukhwi9icwrbompowjo1l8mqqu49 UNIQUE (phone);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_doctor_queue_day_token') THEN
    ALTER TABLE ONLY svc.doctor_queue
    ADD CONSTRAINT uq_doctor_queue_day_token UNIQUE (hospital_id, doctor_id, queue_date, token_no);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_svc_doctor_schedules') THEN
    ALTER TABLE ONLY svc.doctor_schedules
    ADD CONSTRAINT uq_svc_doctor_schedules UNIQUE (doctor_id, day_of_week, start_time);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'user_details_pkey') THEN
    ALTER TABLE ONLY svc.user_details
    ADD CONSTRAINT user_details_pkey PRIMARY KEY (user_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'user_pkey') THEN
    ALTER TABLE ONLY svc."user"
    ADD CONSTRAINT user_pkey PRIMARY KEY (id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'visit_events_pkey') THEN
    ALTER TABLE ONLY svc.visit_events
    ADD CONSTRAINT visit_events_pkey PRIMARY KEY (id);
  END IF;
END $$;

-- ===========================================================================
-- SECTION 3: FOREIGN KEYS (AFTER all tables exist)
-- ===========================================================================

-- Relationship map (summary):
--   hospitals(id)              <- hospital_id on clinical / doctor / booking tables
--   doctor_personal(doctor_id) <- doctor_* profile slices, "user".doctor_id
--   visits(id)                 <- visit_events.visit_id

DO $$
BEGIN
  IF to_regclass('svc.bookings') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_bookings_hospital — table svc.bookings does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bookings_hospital') THEN
    ALTER TABLE svc.bookings ADD CONSTRAINT fk_bookings_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_bookings_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.visits') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_visits_hospital — table svc.visits does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_visits_hospital') THEN
    ALTER TABLE svc.visits ADD CONSTRAINT fk_visits_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_visits_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.visit_events') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_visit_events_hospital — table svc.visit_events does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_visit_events_hospital') THEN
    ALTER TABLE svc.visit_events ADD CONSTRAINT fk_visit_events_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_visit_events_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.notifications') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_notifications_hospital — table svc.notifications does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_notifications_hospital') THEN
    ALTER TABLE svc.notifications ADD CONSTRAINT fk_notifications_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_notifications_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_personal') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_personal_hospital — table svc.doctor_personal does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_personal_hospital') THEN
    ALTER TABLE svc.doctor_personal ADD CONSTRAINT fk_doctor_personal_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_personal_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_contact') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_contact_hospital — table svc.doctor_contact does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_contact_hospital') THEN
    ALTER TABLE svc.doctor_contact ADD CONSTRAINT fk_doctor_contact_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_contact_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_bank') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_bank_hospital — table svc.doctor_bank does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_bank_hospital') THEN
    ALTER TABLE svc.doctor_bank ADD CONSTRAINT fk_doctor_bank_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_bank_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_documents') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_documents_hospital — table svc.doctor_documents does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_documents_hospital') THEN
    ALTER TABLE svc.doctor_documents ADD CONSTRAINT fk_doctor_documents_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_documents_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_identity') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_identity_hospital — table svc.doctor_identity does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_identity_hospital') THEN
    ALTER TABLE svc.doctor_identity ADD CONSTRAINT fk_doctor_identity_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_identity_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_professional') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_professional_hospital — table svc.doctor_professional does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_professional_hospital') THEN
    ALTER TABLE svc.doctor_professional ADD CONSTRAINT fk_doctor_professional_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_professional_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_schedules') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_schedules_hospital — table svc.doctor_schedules does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_schedules_hospital') THEN
    ALTER TABLE svc.doctor_schedules ADD CONSTRAINT fk_doctor_schedules_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_schedules_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_availability') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_availability_hospital — table svc.doctor_availability does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_availability_hospital') THEN
    ALTER TABLE svc.doctor_availability ADD CONSTRAINT fk_doctor_availability_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_availability_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.fee_rules') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_fee_rules_hospital — table svc.fee_rules does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_fee_rules_hospital') THEN
    ALTER TABLE svc.fee_rules ADD CONSTRAINT fk_fee_rules_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_fee_rules_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_clinic') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_clinic_hospital — table svc.doctor_clinic does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_clinic_hospital') THEN
    ALTER TABLE svc.doctor_clinic ADD CONSTRAINT fk_doctor_clinic_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_clinic_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_queue') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_queue_hospital — table svc.doctor_queue does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_queue_hospital') THEN
    ALTER TABLE svc.doctor_queue ADD CONSTRAINT fk_doctor_queue_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_queue_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctors') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctors_hospital — table svc.doctors does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctors_hospital') THEN
    ALTER TABLE svc.doctors ADD CONSTRAINT fk_doctors_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctors_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.hrm_inbox') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_hrm_inbox_hospital — table svc.hrm_inbox does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_hrm_inbox_hospital') THEN
    ALTER TABLE svc.hrm_inbox ADD CONSTRAINT fk_hrm_inbox_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_hrm_inbox_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.hrm_wfh_requests') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_hrm_wfh_hospital — table svc.hrm_wfh_requests does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_hrm_wfh_hospital') THEN
    ALTER TABLE svc.hrm_wfh_requests ADD CONSTRAINT fk_hrm_wfh_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_hrm_wfh_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.leave_applications') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_leave_app_hospital — table svc.leave_applications does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_leave_app_hospital') THEN
    ALTER TABLE svc.leave_applications ADD CONSTRAINT fk_leave_app_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_leave_app_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.appointments') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_appointments_hospital — table svc.appointments does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_appointments_hospital') THEN
    ALTER TABLE svc.appointments ADD CONSTRAINT fk_appointments_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_appointments_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.documents') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_documents_hospital — table svc.documents does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_documents_hospital') THEN
    ALTER TABLE svc.documents ADD CONSTRAINT fk_documents_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_documents_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.login') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_login_hospital — table svc.login does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_login_hospital') THEN
    ALTER TABLE svc.login ADD CONSTRAINT fk_login_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_login_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.patient_reports') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_patient_reports_hospital — table svc.patient_reports does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_patient_reports_hospital') THEN
    ALTER TABLE svc.patient_reports ADD CONSTRAINT fk_patient_reports_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_patient_reports_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.user') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_user_hospital — table svc.user does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_user_hospital') THEN
    ALTER TABLE svc."user" ADD CONSTRAINT fk_user_hospital
      FOREIGN KEY (hospital_id) REFERENCES svc.hospitals(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_user_hospital : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.user') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_user_doctor — table svc.user does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_user_doctor') THEN
    ALTER TABLE svc."user" ADD CONSTRAINT fk_user_doctor
      FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_user_doctor : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_contact') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_contact_personal — table svc.doctor_contact does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_contact_personal') THEN
    ALTER TABLE svc.doctor_contact ADD CONSTRAINT fk_doctor_contact_personal
      FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_contact_personal : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_clinic') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_clinic_personal — table svc.doctor_clinic does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_clinic_personal') THEN
    ALTER TABLE svc.doctor_clinic ADD CONSTRAINT fk_doctor_clinic_personal
      FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_clinic_personal : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_professional') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_professional_personal — table svc.doctor_professional does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_professional_personal') THEN
    ALTER TABLE svc.doctor_professional ADD CONSTRAINT fk_doctor_professional_personal
      FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_professional_personal : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_identity') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_identity_personal — table svc.doctor_identity does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_identity_personal') THEN
    ALTER TABLE svc.doctor_identity ADD CONSTRAINT fk_doctor_identity_personal
      FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_identity_personal : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_bank') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_bank_personal — table svc.doctor_bank does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_bank_personal') THEN
    ALTER TABLE svc.doctor_bank ADD CONSTRAINT fk_doctor_bank_personal
      FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_bank_personal : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.doctor_documents') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_doctor_documents_personal — table svc.doctor_documents does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_documents_personal') THEN
    ALTER TABLE svc.doctor_documents ADD CONSTRAINT fk_doctor_documents_personal
      FOREIGN KEY (doctor_id) REFERENCES svc.doctor_personal(doctor_id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_doctor_documents_personal : %', SQLERRM;
END $$;

DO $$
BEGIN
  IF to_regclass('svc.visit_events') IS NULL THEN
    RAISE NOTICE 'Skip FK fk_visit_events_visit — table svc.visit_events does not exist';
  ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_visit_events_visit') THEN
    ALTER TABLE svc.visit_events ADD CONSTRAINT fk_visit_events_visit
      FOREIGN KEY (visit_id) REFERENCES svc.visits(id);
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'Skip FK fk_visit_events_visit : %', SQLERRM;
END $$;

-- Hospitals sync trigger
DROP TRIGGER IF EXISTS hospitals_sync_hospital_id ON svc.hospitals;
CREATE TRIGGER hospitals_sync_hospital_id
  BEFORE INSERT OR UPDATE OF id ON svc.hospitals
  FOR EACH ROW
  EXECUTE FUNCTION svc.trg_hospitals_sync_hospital_id();

-- End of MedTrack-APP schema.sql

