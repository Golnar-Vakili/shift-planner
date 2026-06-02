package com.employee.controller;

import com.employee.domain.OvertimeRecipientStatus;
import com.employee.security.EmployeeUserDetails;
import com.employee.service.OvertimeRequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequestMapping("/admin/overtime-requests")
public class AdminOvertimeRequestController {

	private final OvertimeRequestService overtimeRequestService;

	public AdminOvertimeRequestController(OvertimeRequestService overtimeRequestService) {
		this.overtimeRequestService = overtimeRequestService;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal EmployeeUserDetails admin, Model model) {
		model.addAttribute("username", admin.getUsername());
		model.addAttribute("displayName", admin.getEmployee().getDisplayName() != null
				? admin.getEmployee().getDisplayName() : admin.getUsername());
		model.addAttribute("activePage", "admin-overtime-requests");
		model.addAttribute("requests", overtimeRequestService.listRequestsForAdmin(admin.getEmployee().getId()));
		return "admin/overtime-requests/list";
	}

	@PostMapping
	public String create(@AuthenticationPrincipal EmployeeUserDetails admin,
	                     @RequestParam String title,
	                     @RequestParam LocalDate workDate,
	                     @RequestParam(required = false) LocalTime startTime,
	                     @RequestParam(required = false) LocalTime endTime,
	                     @RequestParam(required = false) String message,
	                     @RequestParam(name = "employeeIds", required = false) List<Long> employeeIds,
	                     RedirectAttributes ra) {
		try {
			Long id = overtimeRequestService.createRequest(admin.getEmployee().getId(), title, message, workDate, startTime, endTime, employeeIds);
			ra.addFlashAttribute("successMessage", "Overtime request sent.");
			return "redirect:/admin/overtime-requests/" + id;
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/overtime-requests";
		}
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable("id") Long id,
	                     @AuthenticationPrincipal EmployeeUserDetails admin,
	                     Model model,
	                     RedirectAttributes ra) {
		var row = overtimeRequestService.getRequestRow(id);
		if (row.isEmpty()) {
			ra.addFlashAttribute("errorMessage", "Overtime request not found.");
			return "redirect:/admin/overtime-requests";
		}
		model.addAttribute("username", admin.getUsername());
		model.addAttribute("displayName", admin.getEmployee().getDisplayName() != null
				? admin.getEmployee().getDisplayName() : admin.getUsername());
		model.addAttribute("activePage", "admin-overtime-requests");
		model.addAttribute("request", row.get());
		model.addAttribute("recipients", overtimeRequestService.listRecipients(id));
		model.addAttribute("PENDING", OvertimeRecipientStatus.PENDING);
		model.addAttribute("ACCEPTED", OvertimeRecipientStatus.ACCEPTED);
		model.addAttribute("DECLINED", OvertimeRecipientStatus.DECLINED);
		return "admin/overtime-requests/detail";
	}
}

