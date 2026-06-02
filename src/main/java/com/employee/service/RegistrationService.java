package com.employee.service;

import com.employee.domain.ContractType;
import com.employee.domain.Employee;
import com.employee.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

	private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
	public static final int MIN_PASSWORD_LENGTH = 8;

	private final EmployeeRepository employeeRepository;
	private final PasswordEncoder passwordEncoder;

	public RegistrationService(EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder) {
		this.employeeRepository = employeeRepository;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Register with full validation (password match, length, uniqueness).
	 * Contract/hour limits are set automatically from contractType (single place for future extension).
	 */
	@Transactional
	public Employee registerWithConfirmation(ContractType contractType, String username, String password, String confirmPassword) {
		if (username == null || username.isBlank()) {
			throw new IllegalArgumentException("Username is required.");
		}
		if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
		}
		if (!password.equals(confirmPassword)) {
			throw new IllegalArgumentException("Passwords do not match.");
		}
		if (contractType == null) {
			contractType = ContractType.FULL_TIME;
		}
		if (employeeRepository.existsByUsername(username.trim())) {
			throw new IllegalArgumentException("Username already taken. Please choose another.");
		}
		Employee employee = register(contractType, username, password);
		log.info("Saved user {} in MySQL", employee.getUsername());
		return employee;
	}

	@Transactional
	public Employee register(ContractType contractType, String username, String password) {
		if (username == null || username.isBlank()) {
			throw new IllegalArgumentException("Username is required.");
		}
		if (password == null || password.length() < 4) {
			throw new IllegalArgumentException("Password must be at least 4 characters.");
		}
		if (contractType == null) {
			throw new IllegalArgumentException("Contract type is required.");
		}
		if (employeeRepository.existsByUsername(username.trim())) {
			throw new IllegalArgumentException("Username already taken. Please choose another.");
		}
		String hashedPassword = passwordEncoder.encode(password);
		Employee employee = new Employee(username.trim(), hashedPassword, contractType);
		return employeeRepository.save(employee);
	}
}
