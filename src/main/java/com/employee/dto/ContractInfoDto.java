package com.employee.dto;

import java.math.BigDecimal;
import java.util.List;

/** Contract info + weekly progress for Select Shifts bar. */
public record ContractInfoDto(
	String contractTypeLabel,
	BigDecimal requiredHoursPerWeek,
	BigDecimal requiredHoursPerMonth,
	String requiredHoursRule,
	List<WeekSummaryDto> weeklySummaries
) {}
