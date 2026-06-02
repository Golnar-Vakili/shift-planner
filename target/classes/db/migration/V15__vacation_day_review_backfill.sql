-- Backfill per-day review for existing rows (null meant "unspecified" before V14 workflow)

UPDATE vacation_request_days d
INNER JOIN vacation_requests r ON r.id = d.vacation_request_id AND r.deleted_at IS NULL
SET d.review_status = 'PENDING_APPROVAL'
WHERE r.status = 'PENDING' AND d.review_status IS NULL;

UPDATE vacation_request_days d
INNER JOIN vacation_requests r ON r.id = d.vacation_request_id AND r.deleted_at IS NULL
SET d.review_status = 'APPROVED'
WHERE r.status = 'APPROVED' AND d.review_status IS NULL;

UPDATE vacation_request_days d
INNER JOIN vacation_requests r ON r.id = d.vacation_request_id AND r.deleted_at IS NULL
SET d.review_status = 'REJECTED'
WHERE r.status = 'REJECTED' AND d.review_status IS NULL;
