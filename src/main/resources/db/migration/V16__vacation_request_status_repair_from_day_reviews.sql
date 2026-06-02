-- Repair legacy inconsistency:
-- If any day in a request is REJECTED or CHANGES_REQUESTED,
-- and request status is still PENDING/APPROVED, promote request to CHANGES_REQUESTED.

UPDATE vacation_requests r
SET
  r.status = 'CHANGES_REQUESTED',
  r.reviewed_at = COALESCE(
    (
      SELECT MAX(d.reviewed_at)
      FROM vacation_request_days d
      WHERE d.vacation_request_id = r.id
        AND d.review_status IN ('REJECTED', 'CHANGES_REQUESTED')
    ),
    r.reviewed_at
  )
WHERE r.deleted_at IS NULL
  AND r.status IN ('PENDING', 'APPROVED')
  AND EXISTS (
    SELECT 1
    FROM vacation_request_days d
    WHERE d.vacation_request_id = r.id
      AND d.review_status IN ('REJECTED', 'CHANGES_REQUESTED')
  );

