package com.employee.controller;

import com.employee.dto.AdminShiftPlanDetailDto;
import com.employee.dto.AdminShiftEntryReviewUpdateDto;
import com.employee.dto.AdminShiftPlanEntryDto;
import com.employee.domain.ShiftType;
import com.employee.security.EmployeeUserDetails;
import com.employee.service.AdminShiftPlanService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin shift plan approval. All mappings require ROLE_ADMIN (via /admin/**).
 */
@Controller
@RequestMapping("/admin/shift-plans")
public class AdminShiftPlanController {

	private final AdminShiftPlanService adminShiftPlanService;

	public AdminShiftPlanController(AdminShiftPlanService adminShiftPlanService) {
		this.adminShiftPlanService = adminShiftPlanService;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal EmployeeUserDetails admin, Model model) {
		model.addAttribute("username", admin.getUsername());
		model.addAttribute("displayName", admin.getEmployee().getDisplayName() != null
				? admin.getEmployee().getDisplayName() : admin.getUsername());
		model.addAttribute("activePage", "admin-shift-plans");
		model.addAttribute("plans", adminShiftPlanService.listForAdmin());
		return "admin/shift-plans/list";
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable long id,
	                     @AuthenticationPrincipal EmployeeUserDetails admin,
	                     Model model,
	                     RedirectAttributes redirectAttributes) {
		try {
			AdminShiftPlanDetailDto plan = adminShiftPlanService.getDetailForAdmin(id);
			// Build simple model attributes so the template never touches the record (avoids Thymeleaf/record issues)
			model.addAttribute("planId", plan.id());
			model.addAttribute("employeeName", plan.employeeName() != null ? plan.employeeName() : "—");
			model.addAttribute("contractTypeLabel", plan.contractTypeLabel() != null ? plan.contractTypeLabel() : "—");
			model.addAttribute("status", plan.status() != null ? plan.status() : "PENDING_APPROVAL");
			model.addAttribute("submittedAtFormatted", plan.submittedAtFormatted() != null ? plan.submittedAtFormatted() : "—");
			model.addAttribute("reviewedAtFormatted", plan.reviewedAtFormatted());
			model.addAttribute("rejectionReason", plan.rejectionReason());
			model.addAttribute("showApproveReject", "PENDING_APPROVAL".equals(plan.status()));

			// Period: first entry date – last entry date
			List<AdminShiftPlanEntryDto> entries = plan.entries();
			if (entries != null && !entries.isEmpty()) {
				AdminShiftPlanEntryDto first = entries.get(0);
				AdminShiftPlanEntryDto last = entries.get(entries.size() - 1);
				String periodText = (first.date() != null ? first.date().toString() : "") + " – " + (last.date() != null ? last.date().toString() : "");
				model.addAttribute("periodText", periodText.trim());
			} else {
				model.addAttribute("periodText", "—");
			}

			// Expected weekly hours as plain string
			if (plan.expectedWeeklyHours() != null) {
				model.addAttribute("expectedWeeklyHoursText", plan.expectedWeeklyHours().toPlainString() + " h");
			} else {
				model.addAttribute("expectedWeeklyHoursText", "—");
			}

			// Planned total hours as plain string
			model.addAttribute("plannedTotalHoursText", String.format("%.1f h", plan.plannedTotalHours()));

			model.addAttribute("entries", entries != null ? entries : List.of());

			// Layout-related (for standalone we keep it minimal)
			String username = (admin != null && admin.getUsername() != null) ? admin.getUsername() : "";
			model.addAttribute("username", username);
			var employee = admin != null ? admin.getEmployee() : null;
			String displayName = (employee != null && employee.getDisplayName() != null && !employee.getDisplayName().isBlank())
					? employee.getDisplayName() : username;
			model.addAttribute("displayName", displayName != null ? displayName : "");
			model.addAttribute("activePage", "admin-shift-plans");

			return "admin/shift-plans/detail-standalone";
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/shift-plans";
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMessage", "An error occurred while loading the shift plan. Please try again.");
			return "redirect:/admin/shift-plans";
		}
	}

	@PostMapping("/{id}/approve")
	public String approve(@PathVariable long id,
	                     @AuthenticationPrincipal EmployeeUserDetails admin,
	                     RedirectAttributes redirectAttributes) {
		try {
			adminShiftPlanService.approve(id, admin.getEmployee().getId());
			redirectAttributes.addFlashAttribute("successMessage", "Shift plan approved. The user's calendar will show these shifts in green.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/shift-plans";
	}

	@PostMapping("/{id}/review")
	public String review(@PathVariable long id,
	                     @RequestParam(name = "entryId") List<Long> entryIds,
	                     @RequestParam(name = "status") List<String> statuses,
	                     @RequestParam(name = "comment", required = false) List<String> comments,
	                     @AuthenticationPrincipal EmployeeUserDetails admin,
	                     RedirectAttributes redirectAttributes) {
		try {
			long reviewerId = admin.getEmployee().getId();
			List<AdminShiftEntryReviewUpdateDto> updates = new ArrayList<>();
			int n = Math.min(entryIds != null ? entryIds.size() : 0, statuses != null ? statuses.size() : 0);
			for (int i = 0; i < n; i++) {
				Long entryId = entryIds.get(i);
				String status = statuses.get(i);
				String comment = (comments != null && comments.size() > i) ? comments.get(i) : null;
				updates.add(new AdminShiftEntryReviewUpdateDto(entryId, status, comment));
			}
			adminShiftPlanService.reviewEntries(id, reviewerId, updates);
			redirectAttributes.addFlashAttribute("successMessage", "Review saved.");
			return "redirect:/admin/shift-plans/" + id;
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/shift-plans/" + id;
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMessage", "Could not save review.");
			return "redirect:/admin/shift-plans/" + id;
		}
	}

	@PostMapping("/{id}/entries/{entryId}/request-change")
	public String requestChange(@PathVariable long id,
	                            @PathVariable long entryId,
	                            @RequestParam(required = false) String comment,
	                            @AuthenticationPrincipal EmployeeUserDetails admin,
	                            RedirectAttributes redirectAttributes) {
		try {
			adminShiftPlanService.requestChangeEntry(id, entryId, admin.getEmployee().getId(), comment);
			redirectAttributes.addFlashAttribute("successMessage", "Change requested for entry " + entryId + ".");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/shift-plans/" + id;
	}

	@PostMapping("/{id}/entries/{entryId}/cancel")
	public String cancelEntry(@PathVariable long id,
	                          @PathVariable long entryId,
	                          @RequestParam(required = false) String comment,
	                          @AuthenticationPrincipal EmployeeUserDetails admin,
	                          RedirectAttributes redirectAttributes) {
		try {
			adminShiftPlanService.cancelEntry(id, entryId, admin.getEmployee().getId(), comment);
			redirectAttributes.addFlashAttribute("successMessage", "Entry " + entryId + " cancelled.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/shift-plans/" + id;
	}

	@PostMapping("/{id}/entries/{entryId}/update")
	public String updateEntry(@PathVariable long id,
	                          @PathVariable long entryId,
	                          @RequestParam(required = false) ShiftType shiftType,
	                          @RequestParam(required = false) String startTime,
	                          @RequestParam(required = false) String endTime,
	                          @RequestParam(required = false) String comment,
	                          @AuthenticationPrincipal EmployeeUserDetails admin,
	                          RedirectAttributes redirectAttributes) {
		try {
			adminShiftPlanService.adminUpdateEntry(id, entryId, admin.getEmployee().getId(), shiftType, startTime, endTime, comment);
			redirectAttributes.addFlashAttribute("successMessage", "Entry " + entryId + " updated.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMessage", "Could not update entry.");
		}
		return "redirect:/admin/shift-plans/" + id;
	}

	@PostMapping("/{id}/reject")
	public String reject(@PathVariable long id,
	                    @RequestParam(required = false) String reason,
	                    @AuthenticationPrincipal EmployeeUserDetails admin,
	                    RedirectAttributes redirectAttributes) {
		try {
			adminShiftPlanService.reject(id, admin.getEmployee().getId(), reason);
			redirectAttributes.addFlashAttribute("successMessage", "Shift plan rejected. The user can edit or remove shifts and submit again.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/shift-plans";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable long id,
	                     @AuthenticationPrincipal EmployeeUserDetails admin,
	                     RedirectAttributes redirectAttributes) {
		try {
			adminShiftPlanService.softDeletePlan(id, admin.getEmployee().getId());
			redirectAttributes.addFlashAttribute("successMessage", "Shift plan " + id + " removed from the admin list.");
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMessage", "Could not delete shift plan.");
		}
		return "redirect:/admin/shift-plans";
	}

	@PostMapping("/delete-bulk")
	public String deleteBulk(@RequestParam(name = "planIds", required = false) List<Long> planIds,
	                         @AuthenticationPrincipal EmployeeUserDetails admin,
	                         RedirectAttributes redirectAttributes) {
		try {
			if (planIds == null || planIds.isEmpty()) {
				redirectAttributes.addFlashAttribute("errorMessage", "No shift plans selected.");
				return "redirect:/admin/shift-plans";
			}
			adminShiftPlanService.softDeletePlans(planIds, admin.getEmployee().getId());
			redirectAttributes.addFlashAttribute("successMessage", "Selected shift plans removed from the admin list.");
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMessage", "Could not delete selected shift plans.");
		}
		return "redirect:/admin/shift-plans";
	}
}
