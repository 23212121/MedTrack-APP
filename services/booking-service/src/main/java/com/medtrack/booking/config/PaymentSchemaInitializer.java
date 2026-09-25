package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentSchemaInitializer {
  private final JdbcTemplate jdbc;

  public PaymentSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.payments (
          payment_id              VARCHAR(40) PRIMARY KEY,
          appointment_id          VARCHAR(36),
          reference_type          VARCHAR(30) DEFAULT 'APPOINTMENT',
          reference_id            VARCHAR(64),
          patient_id              VARCHAR(64) NOT NULL,
          amount                  DOUBLE PRECISION NOT NULL,
          currency                VARCHAR(10) NOT NULL DEFAULT 'INR',
          status                  VARCHAR(30) NOT NULL,
          gateway_transaction_id  VARCHAR(150),
          gateway_name            VARCHAR(50),
          gateway_order_id        VARCHAR(80),
          gateway_qr_id           VARCHAR(80),
          qr_image_url            VARCHAR(1000),
          upi_uri                 VARCHAR(500),
          created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
          updated_at              TIMESTAMPTZ
        )
        """);
    jdbc.execute("DROP INDEX IF EXISTS svc.uk_payments_gateway_txn");
    jdbc.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_gateway_txn ON svc.payments (gateway_transaction_id) WHERE gateway_transaction_id IS NOT NULL");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_payments_appointment ON svc.payments (appointment_id, status)");
    jdbc.execute("ALTER TABLE svc.payments ALTER COLUMN appointment_id DROP NOT NULL");
    jdbc.execute(
        "ALTER TABLE svc.payments ADD COLUMN IF NOT EXISTS reference_type VARCHAR(30) DEFAULT 'APPOINTMENT'");
    jdbc.execute("ALTER TABLE svc.payments ADD COLUMN IF NOT EXISTS reference_id VARCHAR(64)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_payments_reference ON svc.payments (reference_type, reference_id)");
    jdbc.execute(
        "ALTER TABLE svc.appointments ADD COLUMN IF NOT EXISTS payment_status VARCHAR(30) DEFAULT 'UNPAID'");
    jdbc.execute(
        "ALTER TABLE svc.appointments ADD COLUMN IF NOT EXISTS payment_id VARCHAR(40)");
  }
}
