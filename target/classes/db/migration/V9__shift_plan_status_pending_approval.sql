-- Align status with enum: PENDING -> PENDING_APPROVAL for admin workflow clarity.
UPDATE shift_plans SET status = 'PENDING_APPROVAL' WHERE status = 'PENDING';
