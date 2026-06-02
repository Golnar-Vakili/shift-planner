package com.employee.dto;

/**
 * JSON response for async per-day vacation review on admin detail page.
 */
public record AdminDayReviewAjaxResponse(
		boolean success,
		String message,
		String error,
		AdminVacationDayReviewRowDto dayRow,
		String requestStatus
) {
	public static AdminDayReviewAjaxResponse ok(String message, AdminVacationDayReviewRowDto dayRow, String requestStatus) {
		return new AdminDayReviewAjaxResponse(true, message, null, dayRow, requestStatus);
	}

	public static AdminDayReviewAjaxResponse fail(String error) {
		return new AdminDayReviewAjaxResponse(false, null, error, null, null);
	}
}
