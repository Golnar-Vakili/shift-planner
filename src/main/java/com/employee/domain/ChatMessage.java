package com.employee.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_chat_messages", indexes = {
		@Index(name = "idx_app_chat_messages_sender", columnList = "sender_id"),
		@Index(name = "idx_app_chat_messages_receiver", columnList = "receiver_id"),
		@Index(name = "idx_app_chat_messages_sent_at", columnList = "sent_at")
})
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sender_id", nullable = false)
	private Long senderId;

	@Column(name = "receiver_id", nullable = false)
	private Long receiverId;

	@Column(name = "content", nullable = false, length = 2000)
	private String content;

	// Use a non-reserved, explicit column name to avoid DDL issues across DBs.
	@Column(name = "sent_at", nullable = false)
	private Instant timestamp;

	@Column(name = "read_at")
	private Instant readAt;

	public ChatMessage() {}

	public ChatMessage(Long senderId, Long receiverId, String content, Instant timestamp) {
		this.senderId = senderId;
		this.receiverId = receiverId;
		this.content = content;
		this.timestamp = timestamp;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public Long getSenderId() { return senderId; }
	public void setSenderId(Long senderId) { this.senderId = senderId; }

	public Long getReceiverId() { return receiverId; }
	public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }

	public String getContent() { return content; }
	public void setContent(String content) { this.content = content; }

	public Instant getTimestamp() { return timestamp; }
	public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

	public Instant getReadAt() { return readAt; }
	public void setReadAt(Instant readAt) { this.readAt = readAt; }
}

