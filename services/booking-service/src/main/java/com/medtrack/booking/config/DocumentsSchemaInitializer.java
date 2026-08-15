package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ensures svc.documents exists for hospital patient test-file uploads. */
@Component
public class DocumentsSchemaInitializer {
  private final JdbcTemplate jdbc;

  public DocumentsSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.documents (
          id               VARCHAR(64)  PRIMARY KEY,
          patient_name     VARCHAR(255) NOT NULL,
          aadhaar_number   VARCHAR(20),
          hospital_id      BIGINT       NOT NULL,
          phone_number     VARCHAR(32)  NOT NULL,
          document_type    VARCHAR(64),
          file_path        VARCHAR(1000),
          destination_path VARCHAR(1000),
          source_path      VARCHAR(2000),
          file_upload_1    VARCHAR(1000),
          file_upload_2    VARCHAR(1000),
          file_upload_3    VARCHAR(1000),
          file_upload_4    VARCHAR(1000),
          file_upload_5    VARCHAR(1000),
          creation_date    TIMESTAMPTZ  NOT NULL DEFAULT now(),
          creation_user    VARCHAR(100),
          update_date      TIMESTAMPTZ,
          update_user      VARCHAR(100)
        )
        """);
    try {
      jdbc.execute(
          "ALTER TABLE svc.documents ADD COLUMN IF NOT EXISTS document_type VARCHAR(64)");
    } catch (Exception ignored) {
      // older Postgres without IF NOT EXISTS on ADD COLUMN — ignore
    }
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_documents_hospital ON svc.documents(hospital_id)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_documents_phone ON svc.documents(phone_number)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_documents_aadhaar ON svc.documents(aadhaar_number)");
  }
}
