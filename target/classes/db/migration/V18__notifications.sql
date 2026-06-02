-- App notifications (system + workflow events)

CREATE TABLE IF NOT EXISTS app_notifications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  type VARCHAR(40) NOT NULL,
  message VARCHAR(500) NOT NULL,
  link VARCHAR(300) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at TIMESTAMP NULL,
  PRIMARY KEY (id),
  INDEX idx_app_notif_user_created (user_id, created_at),
  INDEX idx_app_notif_user_read (user_id, read_at),
  INDEX idx_app_notif_type (type)
);

