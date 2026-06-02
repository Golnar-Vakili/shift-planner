package com.employee.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Response for GET /api/vacation-request: entitlement, selected days, status, and dates blocked by shifts.
 */
public record VacationRequestDto(
	int year,
	int entitlementDays,
	int selectedCount,
	int remainingDays,
	String status,
	List<LocalDate> selectedDates,
	List<LocalDate> datesWithShifts,
	List<VacationDayDecisionDto> dayDecisions
) {}
