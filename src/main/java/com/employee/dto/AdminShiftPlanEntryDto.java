package com.employee.dto;

import java.time.LocalDate;

/**
 * One shift entry in the admin shift plan detail (includes review status/comment).
 */
public record AdminShiftPlanEntryDto(
	Long id,
	LocalDate date,
	String startTime,
	String endTime,
	String shiftType,
	int minutes,
	String reviewStatus,
	String reviewComment
) {}
