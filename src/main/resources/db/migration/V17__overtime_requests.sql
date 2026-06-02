-- Overtime requests (admin -> employees), additive only.

CREATE TABLE IF NOT EXISTS app_overtime_requests (
  id BIGINT NOT NULL AUTO_INCREMENT,
  created_by BIGINT NOT NULL,
  title VARCHAR(240) NOT NULL,
  message VARCHAR(2000) NULL,
  work_date DATE NOT NULL,
  start_time TIME NULL,
  end_time TIME NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_app_ot_req_created_by (created_by),
  INDEX idx_app_ot_req_work_date (work_date),
  INDEX idx_app_ot_req_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS app_overtime_request_recipients (
  id BIGINT NOT NULL AUTO_INCREMENT,
  request_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  response_status VARCHAR(20) NOT NULL,
  responded_at TIMESTAMP NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_app_ot_rec_req_emp (request_id, employee_id),
  INDEX idx_app_ot_rec_employee (employee_id),
  INDEX idx_app_ot_rec_request (request_id),
  INDEX idx_app_ot_rec_status (response_status),
  CONSTRAINT fk_app_ot_rec_request
    FOREIGN KEY (request_id) REFERENCES app_overtime_requests(id)
    ON DELETE CASCADE
);

