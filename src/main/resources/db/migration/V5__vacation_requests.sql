-- Vacation request header: one per employee per year, status workflow for admin approval.
CREATE TABLE IF NOT EXISTS vacation_requests (
  id BIGINT NOT NULL AUTO_INCREMENT,
  employee_id BIGINT NOT NULL,
  year INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  submitted_at TIMESTAMP NULL,
  reviewed_at TIMESTAMP NULL,
  reviewed_by_id BIGINT NULL,
  rejection_reason VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_vacation_request_employee_year (employee_id, year),
  KEY idx_vacation_requests_employee (employee_id),
  KEY idx_vacation_requests_status (status),
  CONSTRAINT fk_vacation_request_employee FOREIGN KEY (employee_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_vacation_request_reviewed_by FOREIGN KEY (reviewed_by_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Individual vacation days; uniqueness per request and date.
CREATE TABLE IF NOT EXISTS vacation_request_days (
  id BIGINT NOT NULL AUTO_INCREMENT,
  vacation_request_id BIGINT NOT NULL,
  request_date DATE NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_vacation_request_day (vacation_request_id, request_date),
  KEY idx_vacation_request_days_request (vacation_request_id),
  KEY idx_vacation_request_days_date (request_date),
  CONSTRAINT fk_vacation_request_day_request FOREIGN KEY (vacation_request_id) REFERENCES vacation_requests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
