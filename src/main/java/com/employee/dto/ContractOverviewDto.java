package com.employee.dto;

/**
 * Read-only contract overview for the dashboard Contract page.
 */
public record ContractOverviewDto(
	String contractTypeLabel,
	String hoursPerWeek,
	String hoursPerMonth,
	int vacationDays,
	String planningWindowStart,
	String planningWindowEnd,
	String employmentModelLabel
) {}
