package com.employee.controller;

import com.employee.security.EmployeeUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

	@GetMapping("/")
	public String root() {
		return "redirect:/home";
	}

	@GetMapping("/home")
	public String home(@AuthenticationPrincipal EmployeeUserDetails userDetails, Model model,
	                   @RequestParam(required = false) Boolean registered) {
		model.addAttribute("username", userDetails.getUsername());
		model.addAttribute("contractType", userDetails.getEmployee().getContractType());
		model.addAttribute("displayName", userDetails.getEmployee().getDisplayName() != null
				? userDetails.getEmployee().getDisplayName()
				: userDetails.getUsername());
		if (Boolean.TRUE.equals(registered)) {
			model.addAttribute("successMessage", "Registration successful. Welcome!");
		}
		return "home";
	}
}
