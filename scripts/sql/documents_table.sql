-- Hospital patient test-document uploads
CREATE SCHEMA IF NOT EXISTS svc;

CREATE TABLE IF NOT EXISTS svc.documents (
  id               VARCHAR(64)  PRIMARY KEY,
  patient_name     VARCHAR(255) NOT NULL,
  aadhaar_number   VARCHAR(20),
  hospital_id      BIGINT       NOT NULL,
  phone_number     VARCHAR(32)  NOT NULL,
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
);

CREATE INDEX IF NOT EXISTS idx_documents_hospital ON svc.documents(hospital_id);
CREATE INDEX IF NOT EXISTS idx_documents_phone ON svc.documents(phone_number);
CREATE INDEX IF NOT EXISTS idx_documents_aadhaar ON svc.documents(aadhaar_number);
