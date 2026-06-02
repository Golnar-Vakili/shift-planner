package com.employee.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_overtime_request_recipients", indexes = {
		@Index(name = "idx_app_ot_rec_employee", columnList = "employee_id"),
		@Index(name = "idx_app_ot_rec_request", columnList = "request_id"),
		@Index(name = "idx_app_ot_rec_status", columnList = "response_status")
}, uniqueConstraints = {
		@UniqueConstraint(name = "uq_app_ot_rec_req_emp", columnNames = {"request_id", "employee_id"})
})
public class OvertimeRequestRecipient {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_id", nullable = false)
	private Long requestId;

	@Column(name = "employee_id", nullable = false)
	private Long employeeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "response_status", nullable = false, length = 20)
	private OvertimeRecipientStatus responseStatus = OvertimeRecipientStatus.PENDING;

	@Column(name = "responded_at")
	private Instant respondedAt;

	public OvertimeRequestRecipient() {}

	public OvertimeRequestRecipient(Long requestId, Long employeeId) {
		this.requestId = requestId;
		this.employeeId = employeeId;
		this.responseStatus = OvertimeRecipientStatus.PENDING;
		this.respondedAt = null;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public Long getRequestId() { return requestId; }
	public void setRequestId(Long requestId) { this.requestId = requestId; }

	public Long getEmployeeId() { return employeeId; }
	public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

	public OvertimeRecipientStatus getResponseStatus() { return responseStatus; }
	public void setResponseStatus(OvertimeRecipientStatus responseStatus) { this.responseStatus = responseStatus; }

	public Instant getRespondedAt() { return respondedAt; }
	public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
}

