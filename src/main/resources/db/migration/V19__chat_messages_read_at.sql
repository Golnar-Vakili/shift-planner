-- Persistent read receipts for direct messages (recipient marks incoming messages as read).
ALTER TABLE app_chat_messages
  ADD COLUMN read_at TIMESTAMP NULL;

CREATE INDEX idx_app_chat_messages_receiver_read
  ON app_chat_messages (receiver_id, read_at);

-- Existing history was already seen in practice; avoid flooding users with stale unread badges.
UPDATE app_chat_messages SET read_at = sent_at WHERE read_at IS NULL;
