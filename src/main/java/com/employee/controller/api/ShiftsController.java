package com.employee.controller.api;

import com.employee.domain.ShiftType;
import com.employee.dto.*;
import com.employee.security.EmployeeUserDetails;
import com.employee.service.ShiftPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shifts")
public class ShiftsController {

	private final ShiftPlanService shiftPlanService;

	public ShiftsController(ShiftPlanService shiftPlanService) {
		this.shiftPlanService = shiftPlanService;
	}

	@GetMapping("/window")
	public ResponseEntity<ShiftWindowDto> getWindow(@AuthenticationPrincipal EmployeeUserDetails user) {
		Long userId = user.getEmployee().getId();
		// TEMP DEBUG: visible even if INFO logs are hidden
		System.out.println("[SHIFTS API] GET /api/shifts/window userId=" + userId);
		ShiftWindowDto dto = shiftPlanService.getWindow(userId, user.getEmployee());
		return ResponseEntity.ok(dto);
	}

	@PostMapping("/entry")
	public ResponseEntity<?> addEntry(@AuthenticationPrincipal EmployeeUserDetails user,
	                                  @RequestBody @Valid AddShiftRequest request) {
		Long userId = user.getEmployee().getId();
		try {
			ShiftEntryDto added = shiftPlanService.addEntry(userId, request);
			return ResponseEntity.status(HttpStatus.CREATED).body(added);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@DeleteMapping("/entry/{id}")
	public ResponseEntity<?> deleteEntry(@AuthenticationPrincipal EmployeeUserDetails user, @PathVariable Long id) {
		Long userId = user.getEmployee().getId();
		try {
			shiftPlanService.deleteEntry(userId, id);
			return ResponseEntity.noContent().build();
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/draft")
	public ResponseEntity<?> saveDraft(@AuthenticationPrincipal EmployeeUserDetails user,
	                                   @RequestBody SaveShiftsRequest request) {
		Long userId = user.getEmployee().getId();
		try {
			shiftPlanService.saveDraft(userId, request);
			return ResponseEntity.ok(Map.of("message", "Draft saved."));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/submit")
	public ResponseEntity<?> submit(@AuthenticationPrincipal EmployeeUserDetails user,
	                               @RequestBody SaveShiftsRequest request) {
		Long userId = user.getEmployee().getId();
		try {
			shiftPlanService.submit(userId, request, user.getEmployee());
			return ResponseEntity.ok(Map.of("message", "Shifts submitted for admin approval."));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@GetMapping("/presets")
	public ResponseEntity<Map<String, Object>> getPresets() {
		return ResponseEntity.ok(Map.of(
				"morning", Map.of("start", "08:00", "end", "16:00", "minutes", 480),
				"night", Map.of("start", "22:00", "end", "06:00", "minutes", 480),
				"types", List.of(ShiftType.MORNING.name(), ShiftType.NIGHT.name(), ShiftType.CUSTOM.name())
		));
	}
}
