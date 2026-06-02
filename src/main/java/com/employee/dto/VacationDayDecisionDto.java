package com.employee.dto;

import java.time.LocalDate;

/**
 * Per-day admin decision for an employee vacation request (visibility layer only).
 */
public record VacationDayDecisionDto(
	LocalDate date,
	String reviewStatus,
	String reviewComment
) {}

