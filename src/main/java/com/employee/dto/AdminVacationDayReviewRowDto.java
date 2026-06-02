package com.employee.dto;

import java.time.LocalDate;

/**
 * One vacation day row on the admin vacation request detail page (internal review only).
 */
public record AdminVacationDayReviewRowDto(
	long dayId,
	LocalDate requestDate,
	String reviewStatusCode,
	String reviewStatusLabel,
	String reviewComment,
	String reviewedAtFormatted
) {}
