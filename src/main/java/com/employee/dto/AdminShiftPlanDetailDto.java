package com.employee.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full detail of a shift plan for admin review.
 */
public record AdminShiftPlanDetailDto(
	long id,
	long employeeId,
	String employeeName,
	String contractTypeLabel,
	BigDecimal expectedWeeklyHours,
	double plannedTotalHours,
	List<AdminShiftPlanEntryDto> entries,
	String status,
	Instant submittedAt,
	Instant reviewedAt,
	Long reviewedById,
	String rejectionReason,
	String submittedAtFormatted,
	String reviewedAtFormatted
) {}
