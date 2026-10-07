package com.medtrack.notify.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationSchemaInitializer {
  private static final Logger log = LoggerFactory.getLogger(NotificationSchemaInitializer.class);
  private final JdbcTemplate jdbc;

  public NotificationSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("ALTER TABLE svc.notifications DROP CONSTRAINT IF EXISTS fk_notifications_visit");
    jdbc.execute("ALTER TABLE svc.notifications ALTER COLUMN visit_id DROP NOT NULL");
    log.info("notifications.visit_id is optional (booking, medicine, lab, and HR mail)");
  }
}
