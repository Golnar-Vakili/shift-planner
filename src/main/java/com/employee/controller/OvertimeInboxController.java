package com.employee.controller;

import com.employee.domain.OvertimeRecipientStatus;
import com.employee.security.EmployeeUserDetails;
import com.employee.service.OvertimeRequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/dashboard/overtime-requests")
public class OvertimeInboxController {

	private final OvertimeRequestService overtimeRequestService;

	public OvertimeInboxController(OvertimeRequestService overtimeRequestService) {
		this.overtimeRequestService = overtimeRequestService;
	}

	@GetMapping
	public String inbox(@AuthenticationPrincipal EmployeeUserDetails user, Model model) {
		model.addAttribute("username", user.getUsername());
		model.addAttribute("displayName", user.getEmployee().getDisplayName() != null
				? user.getEmployee().getDisplayName() : user.getUsername());
		model.addAttribute("activePage", "overtime-requests");
		model.addAttribute("items", overtimeRequestService.listInbox(user.getEmployee().getId()));
		return "dashboard/overtime-requests";
	}

	@PostMapping("/{recipientId}/respond")
	public String respond(@PathVariable("recipientId") Long recipientId,
	                      @AuthenticationPrincipal EmployeeUserDetails user,
	                      @RequestParam("response") String response,
	                      RedirectAttributes ra) {
		try {
			OvertimeRecipientStatus status = "ACCEPT".equalsIgnoreCase(response)
					? OvertimeRecipientStatus.ACCEPTED
					: OvertimeRecipientStatus.DECLINED;
			overtimeRequestService.respond(recipientId, user.getEmployee().getId(), status);
			ra.addFlashAttribute("successMessage", "Response saved.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/dashboard/overtime-requests";
	}
}

