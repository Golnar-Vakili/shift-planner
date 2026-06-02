package com.employee.domain;

/**
 * Category for support requests (Help page).
 */
public enum IssueCategory {
	CANT_LOGIN("I can't log in"),
	FORGOT_PASSWORD("I forgot my password"),
	USERNAME_NOT_WORKING("My username doesn't work"),
	OTHER("Other");

	private final String displayLabel;

	IssueCategory(String displayLabel) {
		this.displayLabel = displayLabel;
	}

	public String getDisplayLabel() {
		return displayLabel;
	}
}
