-- Per-entry admin review fields (approve/reject/request changes per shift entry)
ALTER TABLE shift_entries
  ADD COLUMN review_status VARCHAR(30) NULL,
  ADD COLUMN review_comment VARCHAR(500) NULL,
  ADD COLUMN reviewed_at TIMESTAMP NULL,
  ADD COLUMN reviewed_by_id BIGINT NULL,
  ADD CONSTRAINT fk_shift_entry_reviewed_by FOREIGN KEY (reviewed_by_id) REFERENCES users(id) ON DELETE SET NULL;

-- Backfill review_status for existing submitted plans (keep draft entries NULL).
-- When a plan was approved/rejected historically, treat all its entries accordingly.
UPDATE shift_entries e
JOIN shift_plans p ON p.id = e.plan_id
SET e.review_status =
  CASE
    WHEN p.status = 'APPROVED' THEN 'APPROVED'
    WHEN p.status = 'REJECTED' THEN 'REJECTED'
    WHEN p.status = 'PENDING_APPROVAL' THEN 'PENDING_APPROVAL'
    ELSE NULL
  END;

