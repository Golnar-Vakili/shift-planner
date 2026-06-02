package com.employee.controller.api;

import com.employee.security.EmployeeUserDetails;
import com.employee.service.VacationRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Year;
import java.util.Map;

@RestController
@RequestMapping("/api/vacation-request")
public class VacationRequestController {

	private static final Logger log = LoggerFactory.getLogger(VacationRequestController.class);

	private final VacationRequestService vacationRequestService;

	public VacationRequestController(VacationRequestService vacationRequestService) {
		this.vacationRequestService = vacationRequestService;
	}

	private static String safeClientMessage(String message, String fallback) {
		if (message == null || message.isBlank()) {
			return fallback;
		}
		return message;
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
		log.error("Unexpected error in vacation API", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Something went wrong. Please try again."));
	}

	@GetMapping
	public ResponseEntity<?> getRequest(@AuthenticationPrincipal EmployeeUserDetails user,
	                                   @RequestParam(required = false) Integer year) {
		Long employeeId = user.getEmployee().getId();
		int y = year != null ? year : Year.now().getValue();
		return ResponseEntity.ok(vacationRequestService.getRequest(employeeId, y));
	}

	@PostMapping("/days")
	public ResponseEntity<?> addDay(@AuthenticationPrincipal EmployeeUserDetails user,
	                               @RequestBody Map<String, String> body) {
		Long employeeId = user.getEmployee().getId();
		String dateStr = body != null ? body.get("date") : null;
		if (dateStr == null || dateStr.isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("error", "Missing 'date' (YYYY-MM-DD)."));
		}
		LocalDate date;
		try {
			date = LocalDate.parse(dateStr);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format. Use YYYY-MM-DD."));
		}
		int year = date.getYear();
		try {
			vacationRequestService.addDay(employeeId, year, date);
			return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Vacation day added.", "date", dateStr));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", safeClientMessage(e.getMessage(), "Invalid request.")));
		} catch (DataIntegrityViolationException e) {
			log.warn("Vacation add day data constraint: {}", e.getMessage());
			return ResponseEntity.badRequest().body(Map.of("error",
					"This date could not be saved (duplicate or data conflict). Refresh the page and try again."));
		}
	}

	@DeleteMapping("/days/{date}")
	public ResponseEntity<?> removeDay(@AuthenticationPrincipal EmployeeUserDetails user,
	                                  @PathVariable String date) {
		Long employeeId = user.getEmployee().getId();
		LocalDate localDate;
		try {
			localDate = LocalDate.parse(date);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format. Use YYYY-MM-DD."));
		}
		int year = localDate.getYear();
		try {
			vacationRequestService.removeDay(employeeId, year, localDate);
			return ResponseEntity.noContent().build();
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", safeClientMessage(e.getMessage(), "Invalid request.")));
		}
	}

	@PostMapping("/submit")
	public ResponseEntity<?> submit(@AuthenticationPrincipal EmployeeUserDetails user,
	                                @RequestParam(required = false) Integer year) {
		Long employeeId = user.getEmployee().getId();
		int y = year != null ? year : Year.now().getValue();
		try {
			vacationRequestService.submit(employeeId, y);
			return ResponseEntity.ok(Map.of("message", "Vacation request submitted for approval."));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", safeClientMessage(e.getMessage(), "Invalid request.")));
		}
	}
}
