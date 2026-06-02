-- Admin shift approval: submitted_at, reviewed_at, reviewed_by_id, rejection_reason (matches ShiftPlan entity).
ALTER TABLE shift_plans
  ADD COLUMN submitted_at TIMESTAMP NULL,
  ADD COLUMN reviewed_at TIMESTAMP NULL,
  ADD COLUMN reviewed_by_id BIGINT NULL,
  ADD COLUMN rejection_reason VARCHAR(500) NULL,
  ADD CONSTRAINT fk_shift_plan_reviewed_by FOREIGN KEY (reviewed_by_id) REFERENCES users(id) ON DELETE SET NULL;
