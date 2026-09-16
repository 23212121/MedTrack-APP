-- Likes and comments against HRM announcement posts (svc.hrm_posts).
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
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_hrm_post_like_user
  ON svc.hrm_post_likes (post_id, user_id);

CREATE INDEX IF NOT EXISTS idx_hrm_post_likes_hospital_post
  ON svc.hrm_post_likes (hospital_id, post_id);

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
);

CREATE INDEX IF NOT EXISTS idx_hrm_post_comments_post
  ON svc.hrm_post_comments (post_id, creation_date);
