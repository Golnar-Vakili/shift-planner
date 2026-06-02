package com.employee.controller.api;

import com.employee.domain.ShiftPlanStatus;
import com.employee.dto.AdminShiftPlanDetailDto;
import com.employee.dto.AdminShiftPlanListItemDto;
import com.employee.security.EmployeeUserDetails;
import com.employee.service.AdminShiftPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for admin shift plan review: list (optional status filter), detail, approve, reject.
 */
@RestController
@RequestMapping("/api/admin/shift-plans")
public class AdminShiftPlanApiController {

	private final AdminShiftPlanService adminShiftPlanService;

	public AdminShiftPlanApiController(AdminShiftPlanService adminShiftPlanService) {
		this.adminShiftPlanService = adminShiftPlanService;
	}

	@GetMapping
	public ResponseEntity<List<AdminShiftPlanListItemDto>> list(
			@AuthenticationPrincipal EmployeeUserDetails admin,
			@RequestParam(required = false) String status) {
		ShiftPlanStatus statusFilter = null;
		if (status != null && !status.isBlank()) {
			try {
				statusFilter = ShiftPlanStatus.valueOf(status.trim().toUpperCase());
			} catch (IllegalArgumentException e) {
				return ResponseEntity.badRequest().build();
			}
		}
		return ResponseEntity.ok(adminShiftPlanService.listForAdmin(statusFilter));
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> detail(@AuthenticationPrincipal EmployeeUserDetails admin, @PathVariable long id) {
		try {
			AdminShiftPlanDetailDto dto = adminShiftPlanService.getDetailForAdmin(id);
			return ResponseEntity.ok(dto);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.notFound().build();
		}
	}

	@PostMapping("/{id}/approve")
	public ResponseEntity<?> approve(
			@AuthenticationPrincipal EmployeeUserDetails admin,
			@PathVariable long id) {
		try {
			adminShiftPlanService.approve(id, admin.getEmployee().getId());
			return ResponseEntity.ok(Map.of("message", "Shift plan approved."));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/{id}/reject")
	public ResponseEntity<?> reject(
			@AuthenticationPrincipal EmployeeUserDetails admin,
			@PathVariable long id,
			@RequestBody(required = false) Map<String, String> body) {
		String reason = body != null ? body.get("reason") : null;
		try {
			adminShiftPlanService.reject(id, admin.getEmployee().getId(), reason);
			return ResponseEntity.ok(Map.of("message", "Shift plan rejected."));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}
}
