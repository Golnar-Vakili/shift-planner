package com.employee.dto;

import java.time.LocalDate;
import java.util.List;

/** Request to save draft or submit (full list of entries in window). Optional windowStart/windowEnd align backend with frontend window. */
public record SaveShiftsRequest(
	List<ShiftEntryDto> entries,
	LocalDate windowStart,
	LocalDate windowEnd
) {
	/** For backward compatibility: no window, backend uses current date for window. */
	public SaveShiftsRequest(List<ShiftEntryDto> entries) {
		this(entries, null, null);
	}
}
