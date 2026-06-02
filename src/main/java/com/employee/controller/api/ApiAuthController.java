package com.employee.controller.api;

import com.employee.domain.ContractType;
import com.employee.dto.RegisterRequest;
import com.employee.dto.RegisterResponse;
import com.employee.security.EmployeeUserDetails;
import com.employee.security.EmployeeUserDetailsService;
import com.employee.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST API for auth (registration). Future: login, forgot-password, etc.
 */
@RestController
@RequestMapping("/api/auth")
public class ApiAuthController {

	private final RegistrationService registrationService;
	private final EmployeeUserDetailsService userDetailsService;

	public ApiAuthController(RegistrationService registrationService,
	                         EmployeeUserDetailsService userDetailsService) {
		this.registrationService = registrationService;
		this.userDetailsService = userDetailsService;
	}

	@PostMapping("/register")
	public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest request) {
		// Confirm password match (Bean Validation does not cross-field by default)
		if (request.getPassword() != null && !request.getPassword().equals(request.getConfirmPassword())) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Passwords do not match."));
		}

		ContractType contractType = parseContractType(request.getContractType());

		try {
			var employee = registrationService.registerWithConfirmation(
					contractType,
					request.getUsername(),
					request.getPassword(),
					request.getConfirmPassword()
			);

			// Auto-login: establish session so redirect to /dashboard works
			EmployeeUserDetails userDetails = (EmployeeUserDetails) userDetailsService.loadUserByUsername(employee.getUsername());
			var auth = new UsernamePasswordAuthenticationToken(
					userDetails,
					request.getPassword(),
					userDetails.getAuthorities()
			);
			SecurityContextHolder.getContext().setAuthentication(auth);

			RegisterResponse response = new RegisterResponse(
					employee.getId(),
					employee.getUsername(),
					employee.getContractType().name()
			);
			return ResponseEntity.status(HttpStatus.CREATED).body(response);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	private static ContractType parseContractType(String value) {
		if (value == null || value.isBlank()) {
			return ContractType.FULL_TIME;
		}
		String normalized = value.trim().toUpperCase().replace("-", "_");
		if ("MINIJOB".equals(normalized)) {
			return ContractType.MINI_JOB;
		}
		try {
			return ContractType.valueOf(normalized);
		} catch (IllegalArgumentException e) {
			return ContractType.FULL_TIME;
		}
	}
}
