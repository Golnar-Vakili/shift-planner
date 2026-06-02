package com.employee.dto;

/**
 * Lightweight Admin dashboard overview DTO.
 */
public record AdminDashboardOverviewDto(
		long pendingVacationRequests,
		long pendingShiftPlans,
		String activeShiftLabel,
		long activeEmployeesCount,
		long totalEmployeesCount
) {}

