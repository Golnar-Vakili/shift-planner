package com.employee.config;

import com.employee.controller.AdminShiftPlanController;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Catches any exception during admin shift plan detail (or other) handling
 * and redirects to the list with an error message instead of showing a whitelabel error page.
 */
@ControllerAdvice(assignableTypes = AdminShiftPlanController.class)
public class AdminShiftPlanExceptionHandler {

	@ExceptionHandler(Exception.class)
	public String handleAny(Exception ex, RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("errorMessage",
				"Could not load the shift plan. Please try again.");
		return "redirect:/admin/shift-plans";
	}
}
