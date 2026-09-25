-- Public contact form from the MedTrack information site.
CREATE SCHEMA IF NOT EXISTS svc;

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
);

CREATE INDEX IF NOT EXISTS idx_contact_inquiries_created
  ON svc.contact_inquiries(created_at DESC);
