package com.employee.dto;

import java.time.Instant;
import java.util.List;

/**
 * Full detail of a vacation request for admin review.
 */
public record AdminVacationRequestDetailDto(
	long id,
	long employeeId,
	String employeeName,
	int year,
	String contractTypeLabel,
	int entitlement,
	List<AdminVacationDayReviewRowDto> dayRows,
	String status,
	Instant submittedAt,
	Instant reviewedAt,
	Long reviewedById,
	String rejectionReason,
	String reviewComment,
	String submittedAtFormatted,
	String reviewedAtFormatted
) {}
