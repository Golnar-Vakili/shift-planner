-- Annual vacation entitlement per employee (working days per year). Default 20 = German minimum for 5-day week.
ALTER TABLE users
  ADD COLUMN allowed_vacation_days INT NOT NULL DEFAULT 20;
