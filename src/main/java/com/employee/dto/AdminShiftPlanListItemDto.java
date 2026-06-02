package com.employee.dto;

import java.time.Instant;

/**
 * One row in the admin shift plans list.
 */
public record AdminShiftPlanListItemDto(
	long id,
	long employeeId,
	String employeeName,
	String periodStart,
	String periodEnd,
	double totalPlannedHours,
	String status,
	Instant submittedAt,
	String submittedAtFormatted
) {}
