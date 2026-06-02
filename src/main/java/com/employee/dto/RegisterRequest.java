package com.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/auth/register.
 * Future-ready: add fields like email, displayName, teamId here without changing the auth flow.
 */
public class RegisterRequest {

	@NotBlank(message = "Username is required.")
	@Size(min = 2, max = 100)
	private String username;

	@NotBlank(message = "Password is required.")
	@Size(min = 8, message = "Password must be at least 8 characters.")
	private String password;

	@NotBlank(message = "Please confirm your password.")
	private String confirmPassword;

	/** Optional; defaults to FULL_TIME if null or blank. */
	private String contractType;

	public RegisterRequest() {}

	public String getUsername() { return username; }
	public void setUsername(String username) { this.username = username; }
	public String getPassword() { return password; }
	public void setPassword(String password) { this.password = password; }
	public String getConfirmPassword() { return confirmPassword; }
	public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
	public String getContractType() { return contractType; }
	public void setContractType(String contractType) { this.contractType = contractType; }
}
