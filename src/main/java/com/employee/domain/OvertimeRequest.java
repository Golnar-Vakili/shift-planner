package com.employee.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "app_overtime_requests", indexes = {
		@Index(name = "idx_app_ot_req_created_by", columnList = "created_by"),
		@Index(name = "idx_app_ot_req_work_date", columnList = "work_date"),
		@Index(name = "idx_app_ot_req_created_at", columnList = "created_at")
})
public class OvertimeRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(name = "title", nullable = false, length = 240)
	private String title;

	@Column(name = "message", length = 2000)
	private String message;

	@Column(name = "work_date", nullable = false)
	private LocalDate workDate;

	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private OvertimeRequestStatus status = OvertimeRequestStatus.OPEN;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	public OvertimeRequest() {}

	public OvertimeRequest(Long createdBy, String title, String message, LocalDate workDate, LocalTime startTime, LocalTime endTime) {
		this.createdBy = createdBy;
		this.title = title;
		this.message = message;
		this.workDate = workDate;
		this.startTime = startTime;
		this.endTime = endTime;
		this.status = OvertimeRequestStatus.OPEN;
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public Long getCreatedBy() { return createdBy; }
	public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }

	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }

	public LocalDate getWorkDate() { return workDate; }
	public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }

	public LocalTime getStartTime() { return startTime; }
	public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

	public LocalTime getEndTime() { return endTime; }
	public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

	public OvertimeRequestStatus getStatus() { return status; }
	public void setStatus(OvertimeRequestStatus status) { this.status = status; }

	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

