package com.employee.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "support_requests")
public class SupportRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "username_input", length = 100)
	private String usernameInput;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 30)
	private IssueCategory issueCategory;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String message;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RequestStatus status = RequestStatus.OPEN;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	public SupportRequest() {}

	public SupportRequest(IssueCategory issueCategory, String message) {
		this.issueCategory = issueCategory;
		this.message = message;
		this.status = RequestStatus.OPEN;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }
	public String getUsernameInput() { return usernameInput; }
	public void setUsernameInput(String usernameInput) { this.usernameInput = usernameInput; }
	public IssueCategory getIssueCategory() { return issueCategory; }
	public void setIssueCategory(IssueCategory issueCategory) { this.issueCategory = issueCategory; }
	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }
	public RequestStatus getStatus() { return status; }
	public void setStatus(RequestStatus status) { this.status = status; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
