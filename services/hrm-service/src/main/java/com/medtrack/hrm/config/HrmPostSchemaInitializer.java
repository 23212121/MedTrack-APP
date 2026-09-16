package com.medtrack.hrm.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class HrmPostSchemaInitializer {
  private static final Logger log = LoggerFactory.getLogger(HrmPostSchemaInitializer.class);
  private final JdbcTemplate jdbc;

  public HrmPostSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.hrm_posts (
          id              VARCHAR(36) PRIMARY KEY,
          hospital_id     BIGINT NOT NULL,
          doctor_id       VARCHAR(64) NOT NULL,
          title           VARCHAR(240) NOT NULL,
          body            TEXT NOT NULL,
          likes           INTEGER NOT NULL DEFAULT 0,
          comments        INTEGER NOT NULL DEFAULT 0,
          creation_date   TIMESTAMPTZ NOT NULL DEFAULT now(),
          creation_user   VARCHAR(100) NOT NULL,
          update_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
          update_user     VARCHAR(100) NOT NULL
        )
        """);
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_hrm_posts_hospital_doctor ON svc.hrm_posts (hospital_id, doctor_id, creation_date DESC)");
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.hrm_post_likes (
          id              VARCHAR(36) PRIMARY KEY,
          hospital_id     BIGINT NOT NULL,
          post_id         VARCHAR(36) NOT NULL,
          user_id         VARCHAR(64) NOT NULL,
          user_name       VARCHAR(160),
          creation_date   TIMESTAMPTZ NOT NULL DEFAULT now(),
          creation_user   VARCHAR(100) NOT NULL,
          update_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
          update_user     VARCHAR(100) NOT NULL
        )
        """);
    jdbc.execute(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS uk_hrm_post_like_user
          ON svc.hrm_post_likes (post_id, user_id)
        """);
    jdbc.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_hrm_post_likes_hospital_post
          ON svc.hrm_post_likes (hospital_id, post_id)
        """);
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS svc.hrm_post_comments (
          id              VARCHAR(36) PRIMARY KEY,
          hospital_id     BIGINT NOT NULL,
          post_id         VARCHAR(36) NOT NULL,
          user_id         VARCHAR(64) NOT NULL,
          user_name       VARCHAR(160),
          body            TEXT NOT NULL,
          creation_date   TIMESTAMPTZ NOT NULL DEFAULT now(),
          creation_user   VARCHAR(100) NOT NULL,
          update_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
          update_user     VARCHAR(100) NOT NULL
        )
        """);
    jdbc.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_hrm_post_comments_post
          ON svc.hrm_post_comments (post_id, creation_date)
        """);
    log.info("hrm_posts table is ready");
    log.info("hrm_post_likes and hrm_post_comments tables are ready");
  }
}
