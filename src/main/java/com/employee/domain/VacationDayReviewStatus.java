package com.employee.domain;

/**
 * Per-day admin review for vacation request days (admin UI only; not exposed on employee holiday flow).
 */
public enum VacationDayReviewStatus {
	PENDING_APPROVAL,
	APPROVED,
	REJECTED,
	CHANGES_REQUESTED
}
