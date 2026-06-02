package com.employee.controller;

import com.employee.dto.AdminDayReviewAjaxResponse;
import com.employee.security.EmployeeUserDetails;
import com.employee.service.AdminVacationRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/vacation-requests")
public class AdminVacationRequestController {

	private final AdminVacationRequestService adminVacationRequestService;

	public AdminVacationRequestController(AdminVacationRequestService adminVacationRequestService) {
		this.adminVacationRequestService = adminVacationRequestService;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal EmployeeUserDetails admin, Model model) {
		model.addAttribute("username", admin.getUsername());
		model.addAttribute("displayName", admin.getEmployee().getDisplayName() != null
				? admin.getEmployee().getDisplayName() : admin.getUsername());
		model.addAttribute("activePage", "admin-vacation-requests");
		model.addAttribute("requests", adminVacationRequestService.listForAdmin());
		return "admin/vacation-requests/list";
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable long id,
	                     @AuthenticationPrincipal EmployeeUserDetails admin,
	                     Model model,
	                     RedirectAttributes redirectAttributes) {
		try {
			model.addAttribute("username", admin.getUsername());
			model.addAttribute("displayName", admin.getEmployee().getDisplayName() != null
					? admin.getEmployee().getDisplayName() : admin.getUsername());
			model.addAttribute("activePage", "admin-vacation-requests");
			model.addAttribute("request", adminVacationRequestService.getDetailForAdmin(id));
			return "admin/vacation-requests/detail";
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/vacation-requests";
		}
	}

	@PostMapping("/{id}/approve")
	public String approve(@PathVariable long id,
	                      @AuthenticationPrincipal EmployeeUserDetails admin,
	                      RedirectAttributes redirectAttributes) {
		try {
			adminVacationRequestService.approve(id, admin.getEmployee().getId());
			redirectAttributes.addFlashAttribute("successMessage", "Vacation request approved.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/vacation-requests/" + id;
	}

	@PostMapping("/{id}/reject")
	public String reject(@PathVariable long id,
	                     @RequestParam(required = false) String reason,
	                     @AuthenticationPrincipal EmployeeUserDetails admin,
	                     RedirectAttributes redirectAttributes) {
		try {
			adminVacationRequestService.reject(id, admin.getEmployee().getId(), reason);
			redirectAttributes.addFlashAttribute("successMessage", "Vacation request rejected.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/vacation-requests/" + id;
	}

	@PostMapping("/{id}/request-change")
	public String requestChange(@PathVariable long id,
	                            @RequestParam(required = false) String comment,
	                            @AuthenticationPrincipal EmployeeUserDetails admin,
	                            RedirectAttributes redirectAttributes) {
		try {
			adminVacationRequestService.requestChanges(id, admin.getEmployee().getId(), comment);
			redirectAttributes.addFlashAttribute("successMessage", "Changes requested for vacation request " + id + ".");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/vacation-requests/" + id;
	}

	@PostMapping("/{id}/cancel")
	public String cancel(@PathVariable long id,
	                     @RequestParam(required = false) String comment,
	                     @AuthenticationPrincipal EmployeeUserDetails admin,
	                     RedirectAttributes redirectAttributes) {
		try {
			adminVacationRequestService.adminCancel(id, admin.getEmployee().getId(), comment);
			redirectAttributes.addFlashAttribute("successMessage", "Vacation request " + id + " cancelled.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/vacation-requests/" + id;
	}

	@PostMapping("/{id}/update-dates")
	public String updateDates(@PathVariable long id,
	                          @RequestParam(required = false) String dates,
	                          @RequestParam(required = false) String comment,
	                          @AuthenticationPrincipal EmployeeUserDetails admin,
	                          RedirectAttributes redirectAttributes) {
		try {
			List<java.time.LocalDate> parsed = new java.util.ArrayList<>();
			if (dates != null && !dates.isBlank()) {
				for (String part : dates.split("[,\\n]")) {
					String t = part != null ? part.trim() : "";
					if (t.isEmpty()) continue;
					parsed.add(java.time.LocalDate.parse(t));
				}
			}
			adminVacationRequestService.adminUpdateDates(id, admin.getEmployee().getId(), parsed, comment);
			redirectAttributes.addFlashAttribute("successMessage", "Vacation dates updated.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMessage", "Could not update vacation dates.");
		}
		return "redirect:/admin/vacation-requests/" + id;
	}

	@PostMapping(value = "/{id}/days/{dayId}/review", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
			params = "ajax=1", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<AdminDayReviewAjaxResponse> reviewDayAjax(@PathVariable long id,
	                                                                 @PathVariable long dayId,
	                                                                 @RequestParam String decision,
	                                                                 @RequestParam(required = false) String comment,
	                                                                 @AuthenticationPrincipal EmployeeUserDetails admin) {
		try {
			adminVacationRequestService.saveDayReview(id, dayId, decision, comment, admin.getEmployee().getId());
			var row = adminVacationRequestService.getDayReviewRowForRequest(id, dayId);
			String status = adminVacationRequestService.getRequestStatusName(id);
			return ResponseEntity.ok(AdminDayReviewAjaxResponse.ok("Per-day review saved.", row, status));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(AdminDayReviewAjaxResponse.fail(e.getMessage()));
		}
	}

	@PostMapping(value = "/{id}/days/{dayId}/review", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, params = "!ajax")
	public String reviewDay(@PathVariable long id,
	                        @PathVariable long dayId,
	                        @RequestParam String decision,
	                        @RequestParam(required = false) String comment,
	                        @AuthenticationPrincipal EmployeeUserDetails admin,
	                        RedirectAttributes redirectAttributes) {
		try {
			adminVacationRequestService.saveDayReview(id, dayId, decision, comment, admin.getEmployee().getId());
			redirectAttributes.addFlashAttribute("successMessage", "Per-day review saved.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/vacation-requests/" + id;
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable long id,
	                     @AuthenticationPrincipal EmployeeUserDetails admin,
	                     RedirectAttributes redirectAttributes) {
		try {
			adminVacationRequestService.softDeleteRequest(id, admin.getEmployee().getId());
			redirectAttributes.addFlashAttribute("successMessage", "Vacation request " + id + " removed from the admin list.");
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMessage", "Could not delete vacation request.");
		}
		return "redirect:/admin/vacation-requests";
	}

	@PostMapping("/delete-bulk")
	public String deleteBulk(@RequestParam(name = "requestIds", required = false) java.util.List<Long> requestIds,
	                         @AuthenticationPrincipal EmployeeUserDetails admin,
	                         RedirectAttributes redirectAttributes) {
		try {
			if (requestIds == null || requestIds.isEmpty()) {
				redirectAttributes.addFlashAttribute("errorMessage", "No vacation requests selected.");
				return "redirect:/admin/vacation-requests";
			}
			adminVacationRequestService.softDeleteRequests(requestIds, admin.getEmployee().getId());
			redirectAttributes.addFlashAttribute("successMessage", "Selected vacation requests removed from the admin list.");
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMessage", "Could not delete selected vacation requests.");
		}
		return "redirect:/admin/vacation-requests";
	}
}
