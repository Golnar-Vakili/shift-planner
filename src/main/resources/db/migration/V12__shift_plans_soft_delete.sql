-- Soft delete for shift plans (admin cleanup)
-- Keeps historical data but hides rows from admin list and app queries.

ALTER TABLE shift_plans
  ADD COLUMN deleted_at TIMESTAMP NULL AFTER reviewed_by_id,
  ADD COLUMN deleted_by_id BIGINT NULL AFTER deleted_at;

CREATE INDEX idx_shift_plans_deleted_at ON shift_plans (deleted_at);

