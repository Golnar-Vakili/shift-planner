package com.employee.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "vacation_requests", indexes = {
		@Index(name = "uk_vacation_request_employee_year", columnList = "employee_id, year", unique = true),
		@Index(columnList = "employee_id"),
		@Index(columnList = "status")
})
public class VacationRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "employee_id", nullable = false)
	private Long employeeId;

	@Column(name = "year", nullable = false)
	private int year;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private VacationRequestStatus status = VacationRequestStatus.DRAFT;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "reviewed_by_id")
	private Long reviewedById;

	@Column(name = "review_comment", length = 500)
	private String reviewComment;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "deleted_by_id")
	private Long deletedById;

	@Column(name = "rejection_reason", length = 500)
	private String rejectionReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	public void prePersist() {
		Instant now = Instant.now();
		if (createdAt == null) createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	public void preUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public Long getEmployeeId() { return employeeId; }
	public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
	public int getYear() { return year; }
	public void setYear(int year) { this.year = year; }
	public VacationRequestStatus getStatus() { return status; }
	public void setStatus(VacationRequestStatus status) { this.status = status; }
	public Instant getSubmittedAt() { return submittedAt; }
	public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
	public Instant getReviewedAt() { return reviewedAt; }
	public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
	public Long getReviewedById() { return reviewedById; }
	public void setReviewedById(Long reviewedById) { this.reviewedById = reviewedById; }
	public String getReviewComment() { return reviewComment; }
	public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
	public Instant getDeletedAt() { return deletedAt; }
	public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
	public Long getDeletedById() { return deletedById; }
	public void setDeletedById(Long deletedById) { this.deletedById = deletedById; }
	public String getRejectionReason() { return rejectionReason; }
	public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
