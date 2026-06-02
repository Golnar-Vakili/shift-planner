package com.employee.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "shift_plans", indexes = {
		@Index(name = "idx_shift_plans_user_period", columnList = "user_id, period_start, period_end")
})
public class ShiftPlan {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "period_start", nullable = false)
	private LocalDate periodStart;

	@Column(name = "period_end", nullable = false)
	private LocalDate periodEnd;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ShiftPlanStatus status = ShiftPlanStatus.DRAFT;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "reviewed_by_id")
	private Long reviewedById;

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
	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }
	public LocalDate getPeriodStart() { return periodStart; }
	public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
	public LocalDate getPeriodEnd() { return periodEnd; }
	public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
	public ShiftPlanStatus getStatus() { return status; }
	public void setStatus(ShiftPlanStatus status) { this.status = status; }
	public Instant getSubmittedAt() { return submittedAt; }
	public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
	public Instant getReviewedAt() { return reviewedAt; }
	public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
	public Long getReviewedById() { return reviewedById; }
	public void setReviewedById(Long reviewedById) { this.reviewedById = reviewedById; }
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
