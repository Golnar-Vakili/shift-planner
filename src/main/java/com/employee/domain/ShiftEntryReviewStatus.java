package com.employee.domain;

/**
 * Per-entry admin review status for submitted shift entries.
 *
 * DRAFT entries use plan status DRAFT and typically have null review status.
 */
public enum ShiftEntryReviewStatus {
	PENDING_APPROVAL,
	APPROVED,
	REJECTED,
	CHANGES_REQUESTED,
	/** Entry was updated directly by admin after approval. */
	ADMIN_UPDATED,
	/** Entry was cancelled/removed by admin after approval. */
	ADMIN_CANCELLED
}

