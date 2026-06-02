-- Per-day admin review on vacation_request_days (mirrors shift entry review pattern)

ALTER TABLE vacation_request_days
  ADD COLUMN review_status VARCHAR(30) NULL AFTER request_date,
  ADD COLUMN review_comment VARCHAR(500) NULL AFTER review_status,
  ADD COLUMN reviewed_at TIMESTAMP NULL AFTER review_comment,
  ADD COLUMN reviewed_by_id BIGINT NULL AFTER reviewed_at;

CREATE INDEX idx_vacation_request_days_review_status ON vacation_request_days (review_status);

ALTER TABLE vacation_request_days
  ADD CONSTRAINT fk_vacation_request_day_reviewed_by FOREIGN KEY (reviewed_by_id) REFERENCES users(id) ON DELETE SET NULL;
