package com.employee.dto;

/**
 * One per-entry admin review update (approve/reject/request changes) with optional comment.
 */
public record AdminShiftEntryReviewUpdateDto(
	Long entryId,
	String status,
	String comment
) {}

