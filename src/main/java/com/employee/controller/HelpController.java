package com.employee.controller;

import com.employee.domain.IssueCategory;
import com.employee.security.EmployeeUserDetails;
import com.employee.service.SupportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/help")
public class HelpController {

	private final SupportService supportService;

	public HelpController(SupportService supportService) {
		this.supportService = supportService;
	}

	@GetMapping
	public String helpPage(Model model, @RequestParam(required = false) Boolean sent) {
		model.addAttribute("sent", Boolean.TRUE.equals(sent));
		model.addAttribute("categories", IssueCategory.values());
		return "help";
	}

	@PostMapping
	public String submitHelp(@RequestParam(required = false) String emailOrUsername,
	                         @RequestParam String issueCategory,
	                         @RequestParam(required = false) String message,
	                         @AuthenticationPrincipal EmployeeUserDetails principal,
	                         RedirectAttributes redirectAttributes) {
		IssueCategory category;
		try {
			category = IssueCategory.valueOf(issueCategory);
		} catch (Exception e) {
			category = IssueCategory.OTHER;
		}
		Long userId = principal != null ? principal.getEmployee().getId() : null;
		String usernameInput = (emailOrUsername != null && !emailOrUsername.isBlank()) ? emailOrUsername.trim() : null;
		supportService.submitRequest(userId, usernameInput, category, message != null ? message : "");
		redirectAttributes.addAttribute("sent", true);
		return "redirect:/help";
	}
}
