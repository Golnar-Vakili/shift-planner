package com.employee.dto;

import com.employee.domain.ShiftType;

import java.time.LocalDate;

/** Request to add a shift (from drag-drop or form). */
public record AddShiftRequest(
	LocalDate date,
	ShiftType shiftType,
	String startTime,
	String endTime
) {}
