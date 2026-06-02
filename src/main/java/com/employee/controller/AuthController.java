package com.employee.controller;

import com.employee.domain.ContractType;
import com.employee.security.EmployeeUserDetailsService;
import com.employee.service.RegistrationService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

	private final RegistrationService registrationService;
	private final AuthenticationManager authenticationManager;
	private final EmployeeUserDetailsService userDetailsService;

	public AuthController(RegistrationService registrationService,
	                       AuthenticationManager authenticationManager,
	                       EmployeeUserDetailsService userDetailsService) {
		this.registrationService = registrationService;
		this.authenticationManager = authenticationManager;
		this.userDetailsService = userDetailsService;
	}

	@GetMapping("/login")
	public String loginPage(Model model,
	                        @RequestParam(required = false) Boolean error,
	                        @RequestParam(required = false) Boolean logout,
	                        @RequestParam(required = false) String registerError,
	                        @RequestParam(required = false) String activeTab) {
		if (Boolean.TRUE.equals(error)) {
			model.addAttribute("errorMessage", "Invalid username or password. Please try again.");
		}
		if (Boolean.TRUE.equals(logout)) {
			model.addAttribute("logoutMessage", "You have been logged out successfully.");
		}
		if (registerError != null && !registerError.isBlank()) {
			model.addAttribute("registerError", registerError);
		}
		model.addAttribute("activeTab", "register".equals(activeTab) ? "register" : "login");
		model.addAttribute("contractTypes", ContractType.values());
		return "login";
	}

	@PostMapping("/register")
	public String register(@RequestParam String contractType,
	                       @RequestParam String username,
	                       @RequestParam String password,
	                       @RequestParam(required = false) String confirmPassword,
	                       RedirectAttributes redirectAttributes) {
		try {
			ContractType type = ContractType.valueOf(contractType.trim().toUpperCase().replace("-", "_"));
			if (confirmPassword != null && !password.equals(confirmPassword)) {
				redirectAttributes.addAttribute("registerError", "Passwords do not match.");
				redirectAttributes.addAttribute("activeTab", "register");
				return "redirect:/login";
			}
			registrationService.register(type, username, password);
			// Auto-login after successful registration
			UserDetails userDetails = userDetailsService.loadUserByUsername(username.trim());
			var auth = new UsernamePasswordAuthenticationToken(userDetails, password, userDetails.getAuthorities());
			SecurityContextHolder.getContext().setAuthentication(auth);
			redirectAttributes.addAttribute("registered", true);
			return "redirect:/dashboard";
		} catch (IllegalArgumentException e) {
			redirectAttributes.addAttribute("registerError", e.getMessage());
			redirectAttributes.addAttribute("activeTab", "register");
			return "redirect:/login";
		}
	}
}
