package com.employee.dto;

import java.time.Instant;

/**
 * One row in the admin vacation requests list.
 */
public record AdminVacationRequestListItemDto(
	long id,
	long employeeId,
	String employeeName,
	int year,
	int selectedCount,
	String status,
	Instant submittedAt,
	String submittedAtFormatted
) {}
