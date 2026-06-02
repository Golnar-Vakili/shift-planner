package com.employee.controller;

import com.employee.security.EmployeeUserDetails;
import com.employee.service.AdminDashboardService;
import com.employee.service.OvertimeRequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Admin area. All mappings require ROLE_ADMIN.
 * Placeholder dashboard only; approval workflows to be added later.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

	private final AdminDashboardService adminDashboardService;
	private final OvertimeRequestService overtimeRequestService;

	public AdminController(AdminDashboardService adminDashboardService, OvertimeRequestService overtimeRequestService) {
		this.adminDashboardService = adminDashboardService;
		this.overtimeRequestService = overtimeRequestService;
	}

	@GetMapping("/dashboard")
	public String dashboard(@AuthenticationPrincipal EmployeeUserDetails userDetails, Model model) {
		String username = userDetails.getUsername();
		String displayName = userDetails.getEmployee().getDisplayName() != null
				? userDetails.getEmployee().getDisplayName() : username;
		model.addAttribute("username", username);
		model.addAttribute("displayName", displayName);
		model.addAttribute("overview", adminDashboardService.getOverview());
		List<OvertimeRequestService.RequestRow> recent = overtimeRequestService.listRequestsForAdmin(userDetails.getEmployee().getId());
		if (recent.size() > 5) recent = recent.subList(0, 5);
		model.addAttribute("overtimeRecent", recent);
		model.addAttribute("activePage", "admin-home");
		return "admin/dashboard";
	}
}
