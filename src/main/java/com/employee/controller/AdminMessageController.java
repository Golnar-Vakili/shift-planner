package com.employee.controller;

import com.employee.security.EmployeeUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminMessageController {

	@GetMapping("/messages")
	public String messages(@AuthenticationPrincipal EmployeeUserDetails userDetails, Model model) {
		String username = userDetails.getUsername();
		String displayName = userDetails.getEmployee().getDisplayName() != null
				? userDetails.getEmployee().getDisplayName()
				: username;
		model.addAttribute("username", username);
		model.addAttribute("displayName", displayName);
		model.addAttribute("activePage", "admin-messages");
		return "admin/messages";
	}
}

