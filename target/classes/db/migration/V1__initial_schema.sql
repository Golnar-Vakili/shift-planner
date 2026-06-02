-- Initial schema for shiftplanner (MySQL).
-- Ensure database exists: CREATE DATABASE IF NOT EXISTS shiftplanner
--   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Users (login/register)
CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  contract_type ENUM('FULL_TIME','PART_TIME','MINIJOB') NOT NULL,
  allowed_hours_per_week DECIMAL(5,2) NOT NULL,
  allowed_hours_per_month DECIMAL(6,2) NULL,
  display_name VARCHAR(200) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Support / Help requests
CREATE TABLE IF NOT EXISTS support_requests (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  username_input VARCHAR(100) NULL,
  category ENUM('CANT_LOGIN','FORGOT_PASSWORD','USERNAME_NOT_WORKING','OTHER') NOT NULL,
  message TEXT NOT NULL,
  status ENUM('OPEN','IN_PROGRESS','CLOSED') NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_support_user_id (user_id),
  CONSTRAINT fk_support_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
