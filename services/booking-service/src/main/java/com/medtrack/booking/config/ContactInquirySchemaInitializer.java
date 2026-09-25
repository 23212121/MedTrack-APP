package com.medtrack.booking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Public contact form inquiries from the MedTrack information site. */
@Component
public class ContactInquirySchemaInitializer {
  private final JdbcTemplate jdbc;

  public ContactInquirySchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS svc");
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.contact_inquiries (
          inquiry_id    VARCHAR(36)   PRIMARY KEY,
          full_name     VARCHAR(255)  NOT NULL,
          email         VARCHAR(255)  NOT NULL,
          phone         VARCHAR(40),
          organization  VARCHAR(255),
          subject       VARCHAR(255)  NOT NULL,
          message       VARCHAR(4000) NOT NULL,
          status        VARCHAR(32)   NOT NULL DEFAULT 'NEW',
          created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
        )
        """);
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_contact_inquiries_created ON svc.contact_inquiries(created_at DESC)");
  }
}
