package com.employee.domain;

/**
 * Lifecycle status of an annual vacation request.
 * DRAFT = employee is still selecting dates; PENDING = submitted for admin approval;
 * APPROVED / REJECTED = admin decision.
 */
public enum VacationRequestStatus {
	DRAFT,
	PENDING,
	APPROVED,
	REJECTED,
	/** Admin requests the employee to adjust dates and resubmit. */
	CHANGES_REQUESTED,
	/** Request was updated directly by admin (dates changed) for operational reasons. */
	ADMIN_UPDATED,
	/** Request was cancelled/voided by admin after approval. */
	ADMIN_CANCELLED
}
