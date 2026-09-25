package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Medicine order tables + demo medical store login under hospital 10001. */
@Component
public class MedicineOrderSchemaInitializer {
  private final JdbcTemplate jdbc;

  public MedicineOrderSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.medical_stores (
          id             VARCHAR(64) PRIMARY KEY,
          hospital_id    BIGINT NOT NULL,
          store_code     VARCHAR(40) NOT NULL,
          store_name     VARCHAR(255) NOT NULL,
          phone          VARCHAR(32),
          address        VARCHAR(500),
          city            VARCHAR(120),
          state           VARCHAR(120),
          status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
          created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
          created_by     VARCHAR(100),
          updated_at     TIMESTAMPTZ,
          updated_by     VARCHAR(100)
        )
        """);
    jdbc.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS uk_medical_store_code ON svc.medical_stores (store_code)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_medical_store_hospital ON svc.medical_stores (hospital_id)");

    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.medicine_orders (
          id                  VARCHAR(64) PRIMARY KEY,
          order_number        VARCHAR(40) NOT NULL,
          hospital_id         BIGINT NOT NULL,
          patient_id          VARCHAR(64),
          patient_name        VARCHAR(255) NOT NULL,
          patient_phone       VARCHAR(32) NOT NULL,
          doctor_id           VARCHAR(64),
          doctor_name         VARCHAR(255),
          fulfillment         VARCHAR(20) NOT NULL DEFAULT 'PICKUP',
          delivery_address    VARCHAR(500),
          notes               VARCHAR(1000),
          status              VARCHAR(40) NOT NULL DEFAULT 'PENDING',
          status_code         INTEGER NOT NULL DEFAULT 8,
          assigned_store_id   VARCHAR(64),
          assigned_store_name VARCHAR(255),
          current_amount      DOUBLE PRECISION,
          amount_status       VARCHAR(32) DEFAULT 'NOT_CALCULATED',
          booked_by           VARCHAR(20) NOT NULL,
          cancel_reason       VARCHAR(500),
          cancelled_by        VARCHAR(100),
          cancelled_at        TIMESTAMPTZ,
          completed_at        TIMESTAMPTZ,
          completed_by        VARCHAR(100),
          created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
          created_by          VARCHAR(100),
          updated_at          TIMESTAMPTZ,
          updated_by          VARCHAR(100)
        )
        """);
    jdbc.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS uk_medicine_order_number ON svc.medicine_orders (order_number)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_medicine_order_hospital ON svc.medicine_orders (hospital_id, status)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_medicine_order_phone ON svc.medicine_orders (patient_phone)");
    jdbc.execute(
        "ALTER TABLE svc.medicine_orders ADD COLUMN IF NOT EXISTS pending_reason VARCHAR(500)");
    jdbc.execute(
        "ALTER TABLE svc.medicine_orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(32) DEFAULT 'UNPAID'");
    jdbc.execute(
        "ALTER TABLE svc.medicine_orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(32)");
    jdbc.execute(
        "ALTER TABLE svc.medicine_orders ADD COLUMN IF NOT EXISTS razorpay_order_id VARCHAR(80)");
    jdbc.execute(
        "ALTER TABLE svc.medicine_orders ADD COLUMN IF NOT EXISTS razorpay_payment_id VARCHAR(80)");
    jdbc.execute(
        "ALTER TABLE svc.medical_stores ADD COLUMN IF NOT EXISTS upi_id VARCHAR(120)");
    jdbc.execute(
        "ALTER TABLE svc.medical_stores ADD COLUMN IF NOT EXISTS city VARCHAR(120)");
    jdbc.execute(
        "ALTER TABLE svc.medical_stores ADD COLUMN IF NOT EXISTS state VARCHAR(120)");
    jdbc.execute(
        """
        UPDATE svc.medical_stores s
           SET city = h.city,
               state = h.state
          FROM svc.hospitals h
         WHERE s.hospital_id = h.id
           AND (s.city IS NULL OR btrim(s.city) = '')
        """);
    jdbc.execute(
        "ALTER TABLE svc.medicine_order_documents ADD COLUMN IF NOT EXISTS kind VARCHAR(32) NOT NULL DEFAULT 'PRESCRIPTION'");

    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.medicine_order_items (
          id                 VARCHAR(64) PRIMARY KEY,
          order_id           VARCHAR(64) NOT NULL,
          prescribed_name    VARCHAR(255) NOT NULL,
          medicine_name      VARCHAR(255) NOT NULL,
          quantity           DOUBLE PRECISION NOT NULL,
          unit_price         DOUBLE PRECISION NOT NULL,
          line_total         DOUBLE PRECISION NOT NULL,
          availability       VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
          substitute_name    VARCHAR(255),
          substitute_reason  VARCHAR(500),
          days               INTEGER NOT NULL DEFAULT 30,
          requested_days     INTEGER NOT NULL DEFAULT 30,
          quoted_quantity    DOUBLE PRECISION NOT NULL DEFAULT 0,
          sort_order         INTEGER NOT NULL DEFAULT 0
        )
        """);
        jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_medicine_order_items_order ON svc.medicine_order_items (order_id)");
    jdbc.execute(
        "ALTER TABLE svc.medicine_order_items ADD COLUMN IF NOT EXISTS days INTEGER NOT NULL DEFAULT 30");
    jdbc.execute(
        "ALTER TABLE svc.medicine_order_items ADD COLUMN IF NOT EXISTS requested_days INTEGER NOT NULL DEFAULT 30");
    jdbc.execute(
        "ALTER TABLE svc.medicine_order_items ADD COLUMN IF NOT EXISTS quoted_quantity DOUBLE PRECISION NOT NULL DEFAULT 0");

    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.medicine_order_charges (
          order_id           VARCHAR(64) PRIMARY KEY,
          delivery_charge    DOUBLE PRECISION NOT NULL DEFAULT 0,
          packaging_charge   DOUBLE PRECISION NOT NULL DEFAULT 0,
          tax                DOUBLE PRECISION NOT NULL DEFAULT 0,
          other_charges      DOUBLE PRECISION NOT NULL DEFAULT 0,
          discount           DOUBLE PRECISION NOT NULL DEFAULT 0,
          medicine_subtotal  DOUBLE PRECISION NOT NULL DEFAULT 0,
          grand_total        DOUBLE PRECISION NOT NULL DEFAULT 0,
          draft              BOOLEAN NOT NULL DEFAULT TRUE,
          quote_version      INTEGER NOT NULL DEFAULT 1
        )
        """);

    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.medicine_order_documents (
          id             VARCHAR(64) PRIMARY KEY,
          order_id       VARCHAR(64) NOT NULL,
          file_name      VARCHAR(255) NOT NULL,
          content_type   VARCHAR(120),
          file_path      VARCHAR(1000) NOT NULL,
          latest         BOOLEAN NOT NULL DEFAULT TRUE,
          created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
          created_by     VARCHAR(100)
        )
        """);
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_medicine_order_docs ON svc.medicine_order_documents (order_id)");

    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.medicine_order_amount_history (
          id             VARCHAR(64) PRIMARY KEY,
          order_id       VARCHAR(64) NOT NULL,
          version        INTEGER NOT NULL,
          grand_total    DOUBLE PRECISION NOT NULL,
          snapshot_json  TEXT,
          status         VARCHAR(32) NOT NULL,
          reason         VARCHAR(500),
          created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
          created_by     VARCHAR(100)
        )
        """);

    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.medicine_order_status_history (
          id              VARCHAR(64) PRIMARY KEY,
          order_id        VARCHAR(64) NOT NULL,
          previous_status VARCHAR(40),
          new_status      VARCHAR(40) NOT NULL,
          actor           VARCHAR(100),
          note            VARCHAR(500),
          created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
        )
        """);

    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.medicine_order_pharmacy_response (
          id             VARCHAR(64) PRIMARY KEY,
          order_id       VARCHAR(64) NOT NULL,
          store_id       VARCHAR(64) NOT NULL,
          action         VARCHAR(20) NOT NULL,
          reason         VARCHAR(500),
          created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
        )
        """);

    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.medicine_order_notifications (
          id             VARCHAR(64) PRIMARY KEY,
          hospital_id    BIGINT NOT NULL,
          store_id       VARCHAR(64),
          patient_phone  VARCHAR(32),
          audience       VARCHAR(20) NOT NULL,
          order_id       VARCHAR(64),
          title          VARCHAR(255) NOT NULL,
          message        VARCHAR(1000) NOT NULL,
          read_flag      BOOLEAN NOT NULL DEFAULT FALSE,
          created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
        )
        """);
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_med_order_notif_store ON svc.medicine_order_notifications (store_id, read_flag)");

    seedDemoStore();
  }

  private void seedDemoStore() {
    jdbc.update(
        """
        INSERT INTO svc.medical_stores (
          id, hospital_id, store_code, store_name, phone, address, status, created_at, created_by
        ) VALUES (
          'store-10001-1', 10001, 'MED-10001-1', 'Test Hospital Pharmacy',
          '9876500001', 'Ground floor, Test Hospital', 'ACTIVE', now(), 'seed'
        )
        ON CONFLICT (id) DO UPDATE SET
          store_name = EXCLUDED.store_name,
          status = 'ACTIVE'
        """);
    jdbc.update(
        """
        UPDATE svc.medical_stores SET upi_id = 'medtrackpharmacy@upi'
        WHERE id = 'store-10001-1' AND (upi_id IS NULL OR upi_id = '')
        """);
    jdbc.update(
        """
        INSERT INTO svc.login (
          id, login_type, login_id, password, hospital_id, display_name,
          status, creation_date, creation_user
        ) VALUES (
          'login-med-10001-1', 'MEDICAL', 'MED-10001-1', '123456', 10001,
          'Test Hospital Pharmacy', 'ACTIVE', now(), 'seed'
        )
        ON CONFLICT (id) DO UPDATE SET
          password = EXCLUDED.password,
          hospital_id = EXCLUDED.hospital_id,
          display_name = EXCLUDED.display_name,
          status = 'ACTIVE'
        """);
    jdbc.update(
        """
        UPDATE svc.medical_stores s
           SET city = h.city,
               state = h.state
          FROM svc.hospitals h
         WHERE s.hospital_id = h.id
           AND (s.city IS NULL OR btrim(s.city) = '')
        """);
    jdbc.update(
        """
        UPDATE svc.medical_stores
           SET city = COALESCE(NULLIF(btrim(city), ''), 'Hyderabad'),
               state = COALESCE(NULLIF(btrim(state), ''), 'Telangana')
         WHERE id = 'store-10001-1'
        """);
  }
}
