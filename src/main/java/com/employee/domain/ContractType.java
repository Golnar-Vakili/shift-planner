package com.employee.domain;

import java.math.BigDecimal;

/**
 * Employee contract types with default working-hour limits and vacation entitlement.
 * Vollzeit = Full-time, Teilzeit = Part-time, Minijob = Mini-job.
 */
public enum ContractType {
	FULL_TIME(40, null, 20),
	PART_TIME(20, null, 20),
	MINI_JOB(10, 42, 20);

	private final int defaultHoursPerWeek;
	private final Integer defaultHoursPerMonth;
	private final int defaultVacationDays;

	ContractType(int defaultHoursPerWeek, Integer defaultHoursPerMonth, int defaultVacationDays) {
		this.defaultHoursPerWeek = defaultHoursPerWeek;
		this.defaultHoursPerMonth = defaultHoursPerMonth;
		this.defaultVacationDays = defaultVacationDays;
	}

	public BigDecimal getDefaultHoursPerWeek() {
		return BigDecimal.valueOf(defaultHoursPerWeek);
	}

	/** Null for full-time and part-time (use weekly limit); 42 for mini-job. */
	public BigDecimal getDefaultHoursPerMonth() {
		return defaultHoursPerMonth != null ? BigDecimal.valueOf(defaultHoursPerMonth) : null;
	}

	/** Default annual vacation days (working days). German minimum typically 20 for 5-day week. */
	public int getDefaultVacationDays() {
		return defaultVacationDays;
	}
}
