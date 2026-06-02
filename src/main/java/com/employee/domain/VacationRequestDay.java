package com.employee.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "vacation_request_days", indexes = {
		@Index(name = "uk_vacation_request_day", columnList = "vacation_request_id, request_date", unique = true),
		@Index(columnList = "vacation_request_id"),
		@Index(columnList = "request_date")
})
public class VacationRequestDay {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "vacation_request_id", nullable = false)
	private Long vacationRequestId;

	@Column(name = "request_date", nullable = false)
	private LocalDate requestDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "review_status", length = 30)
	private VacationDayReviewStatus reviewStatus;

	@Column(name = "review_comment", length = 500)
	private String reviewComment;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "reviewed_by_id")
	private Long reviewedById;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public Long getVacationRequestId() { return vacationRequestId; }
	public void setVacationRequestId(Long vacationRequestId) { this.vacationRequestId = vacationRequestId; }
	public LocalDate getRequestDate() { return requestDate; }
	public void setRequestDate(LocalDate requestDate) { this.requestDate = requestDate; }
	public VacationDayReviewStatus getReviewStatus() { return reviewStatus; }
	public void setReviewStatus(VacationDayReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }
	public String getReviewComment() { return reviewComment; }
	public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
	public Instant getReviewedAt() { return reviewedAt; }
	public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
	public Long getReviewedById() { return reviewedById; }
	public void setReviewedById(Long reviewedById) { this.reviewedById = reviewedById; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
