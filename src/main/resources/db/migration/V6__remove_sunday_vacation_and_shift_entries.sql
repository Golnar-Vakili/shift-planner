-- Remove invalid vacation and shift entries for Sundays (business rule: Sundays are non-working).
-- DAYOFWEEK(date) in MySQL: 1 = Sunday, 2 = Monday, ... 7 = Saturday.
DELETE FROM vacation_request_days WHERE DAYOFWEEK(request_date) = 1;
DELETE FROM shift_entries WHERE DAYOFWEEK(entry_date) = 1;
