package com.employee.dto;

import java.time.LocalDate;
import java.math.BigDecimal;

/** Summary of selected hours for one week in the planning window. */
public record WeekSummaryDto(
	LocalDate weekStart,
	BigDecimal selectedHours,
	BigDecimal requiredHours,
	boolean valid
) {}
