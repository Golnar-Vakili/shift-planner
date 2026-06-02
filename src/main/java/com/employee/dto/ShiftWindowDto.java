package com.employee.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Response for GET /api/shifts/window: allowed range, entries, contract info, holidays. */
public record ShiftWindowDto(
	LocalDate allowedStart,
	LocalDate allowedEnd,
	List<ShiftEntryDto> entries,
	ContractInfoDto contractInfo,
	List<HolidayDto> holidays,
	String planStatus
) {
	public ShiftWindowDto(LocalDate allowedStart, LocalDate allowedEnd, List<ShiftEntryDto> entries,
	                      ContractInfoDto contractInfo, List<HolidayDto> holidays) {
		this(allowedStart, allowedEnd, entries, contractInfo, holidays, null);
	}
}
