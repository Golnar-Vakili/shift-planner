package com.employee.dto;

/**
 * Response for successful registration. No password or hash is ever returned.
 */
public class RegisterResponse {

	private Long id;
	private String username;
	private String contractType;
	private String message;

	public RegisterResponse() {}

	public RegisterResponse(Long id, String username, String contractType) {
		this.id = id;
		this.username = username;
		this.contractType = contractType;
		this.message = "Account created successfully.";
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getUsername() { return username; }
	public void setUsername(String username) { this.username = username; }
	public String getContractType() { return contractType; }
	public void setContractType(String contractType) { this.contractType = contractType; }
	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }
}
