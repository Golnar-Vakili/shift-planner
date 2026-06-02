-- Admin flag for role-based access: admins get ROLE_ADMIN and redirect to /admin/dashboard.
ALTER TABLE users
  ADD COLUMN admin BOOLEAN NOT NULL DEFAULT FALSE;
