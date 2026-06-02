package com.employee.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_notifications", indexes = {
		@Index(name = "idx_app_notif_user_created", columnList = "user_id, created_at"),
		@Index(name = "idx_app_notif_user_read", columnList = "user_id, read_at"),
		@Index(name = "idx_app_notif_type", columnList = "type")
})
public class AppNotification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 40)
	private NotificationType type;

	@Column(name = "message", nullable = false, length = 500)
	private String message;

	@Column(name = "link", length = 300)
	private String link;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "read_at")
	private Instant readAt;

	public AppNotification() {}

	public AppNotification(Long userId, NotificationType type, String message, String link) {
		this.userId = userId;
		this.type = type;
		this.message = message;
		this.link = link;
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public NotificationType getType() { return type; }
	public void setType(NotificationType type) { this.type = type; }

	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }

	public String getLink() { return link; }
	public void setLink(String link) { this.link = link; }

	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

	public Instant getReadAt() { return readAt; }
	public void setReadAt(Instant readAt) { this.readAt = readAt; }
}

