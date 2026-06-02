package com.employee.controller;

import com.employee.security.EmployeeUserDetails;
import com.employee.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard/api/notifications")
public class NotificationApiController {

	private final NotificationService notificationService;

	public NotificationApiController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	public NotificationService.NotificationFeed feed(@AuthenticationPrincipal EmployeeUserDetails user,
	                                                @RequestParam(name = "limit", required = false, defaultValue = "8") int limit) {
		return notificationService.getFeed(user.getEmployee().getId(), limit);
	}

	@PostMapping("/{id}/read")
	public void read(@PathVariable("id") Long id, @AuthenticationPrincipal EmployeeUserDetails user) {
		notificationService.markRead(id, user.getEmployee().getId());
	}
}

