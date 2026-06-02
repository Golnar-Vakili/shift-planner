-- Admin flexibility & cleanup for vacation requests

ALTER TABLE vacation_requests
  ADD COLUMN review_comment VARCHAR(500) NULL AFTER reviewed_by_id,
  ADD COLUMN deleted_at TIMESTAMP NULL AFTER review_comment,
  ADD COLUMN deleted_by_id BIGINT NULL AFTER deleted_at;

CREATE INDEX idx_vacation_requests_deleted_at ON vacation_requests (deleted_at);

