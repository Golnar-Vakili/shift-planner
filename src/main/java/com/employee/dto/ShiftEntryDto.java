package com.employee.dto;

import com.employee.domain.ShiftType;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One shift entry for API (create/update or response).
 *
 * - planStatus: overall plan lifecycle status (DRAFT / PENDING_APPROVAL / APPROVED / REJECTED)
 * - entryStatus: per-entry admin review status (PENDING_APPROVAL / APPROVED / REJECTED / CHANGES_REQUESTED) or DRAFT
 * - readOnly: true if returned for display only (e.g., outside window)
 * - editable/deletable: per-entry permissions for the user UI
 */
public record ShiftEntryDto(
	Long id,
	LocalDate date,
	String startTime,
	String endTime,
	ShiftType shiftType,
	int minutes,
	String planStatus,
	String entryStatus,
	String reviewComment,
	Boolean editable,
	Boolean deletable,
	Boolean readOnly
) {
	public static ShiftEntryDto of(Long id, LocalDate date, LocalTime start, LocalTime end, ShiftType type, int minutes) {
		return new ShiftEntryDto(id, date, start.toString(), end.toString(), type, minutes, null, null, null, null, null, false);
	}

	public static ShiftEntryDto ofDraft(Long id, LocalDate date, LocalTime start, LocalTime end, ShiftType type, int minutes) {
		return new ShiftEntryDto(id, date, start.toString(), end.toString(), type, minutes, "DRAFT", "DRAFT", null, true, true, false);
	}

	public static ShiftEntryDto ofWindow(Long id, LocalDate date, LocalTime start, LocalTime end, ShiftType type, int minutes,
	                                    String planStatus, String entryStatus, String reviewComment,
	                                    boolean editable, boolean deletable, boolean readOnly) {
		return new ShiftEntryDto(id, date, start.toString(), end.toString(), type, minutes, planStatus, entryStatus, reviewComment, editable, deletable, readOnly);
	}
}
