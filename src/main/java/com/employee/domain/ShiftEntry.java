package com.employee.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "shift_entries", indexes = {
		@Index(columnList = "plan_id"),
		@Index(columnList = "plan_id, entry_date")
})
public class ShiftEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "plan_id", nullable = false)
	private Long planId;

	@Column(name = "entry_date", nullable = false)
	private LocalDate entryDate;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "shift_type", nullable = false, length = 20)
	private ShiftType shiftType;

	@Column(name = "minutes", nullable = false)
	private int minutes;

	@Enumerated(EnumType.STRING)
	@Column(name = "review_status", length = 30)
	private ShiftEntryReviewStatus reviewStatus;

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
	public Long getPlanId() { return planId; }
	public void setPlanId(Long planId) { this.planId = planId; }
	public LocalDate getEntryDate() { return entryDate; }
	public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
	public LocalTime getStartTime() { return startTime; }
	public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
	public LocalTime getEndTime() { return endTime; }
	public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
	public ShiftType getShiftType() { return shiftType; }
	public void setShiftType(ShiftType shiftType) { this.shiftType = shiftType; }
	public int getMinutes() { return minutes; }
	public void setMinutes(int minutes) { this.minutes = minutes; }
	public ShiftEntryReviewStatus getReviewStatus() { return reviewStatus; }
	public void setReviewStatus(ShiftEntryReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }
	public String getReviewComment() { return reviewComment; }
	public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
	public Instant getReviewedAt() { return reviewedAt; }
	public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
	public Long getReviewedById() { return reviewedById; }
	public void setReviewedById(Long reviewedById) { this.reviewedById = reviewedById; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
