package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CareChatSchemaInitializer {
  private static final Logger log = LoggerFactory.getLogger(CareChatSchemaInitializer.class);
  private final JdbcTemplate jdbc;

  public CareChatSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.care_chats (
          id              VARCHAR(36) PRIMARY KEY,
          hospital_id     BIGINT NOT NULL,
          doctor_id       VARCHAR(64) NOT NULL,
          appointment_id  VARCHAR(64),
          patient_name    VARCHAR(255) NOT NULL,
          patient_phone   VARCHAR(32),
          sender_type     VARCHAR(20) NOT NULL,
          sender_name     VARCHAR(255),
          message_text    TEXT NOT NULL,
          document_url    VARCHAR(1000),
          document_name   VARCHAR(255),
          created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
          created_user    VARCHAR(100)
        )
        """);
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_care_chats_appointment ON svc.care_chats (appointment_id)");
    log.info("care_chats table is ready");
  }
}
