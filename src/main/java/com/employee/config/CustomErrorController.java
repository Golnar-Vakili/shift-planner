package com.employee.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Handles /error so that 500s on admin shift plan detail (e.g. view rendering)
 * redirect to the shift plans list instead of showing the whitelabel error page.
 * Other errors show a simple error page.
 */
@Controller
public class CustomErrorController implements ErrorController {

	private static final Logger log = LoggerFactory.getLogger(CustomErrorController.class);
	private static final String ERROR_PATH = "/error";

	@RequestMapping(ERROR_PATH)
	public String handleError(HttpServletRequest request, Model model, RedirectAttributes redirectAttributes) {
		Integer status = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
		Throwable ex = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
		if (ex != null) {
			log.error("Error while handling request {}: status={}", requestUri, status, ex);
		}
		if (status != null && status == HttpStatus.INTERNAL_SERVER_ERROR.value()
				&& requestUri != null && requestUri.matches("/admin/shift-plans/\\d+")) {
			redirectAttributes.addFlashAttribute("errorMessage",
					"Could not load the shift plan. Please try again.");
			return "redirect:/admin/shift-plans";
		}
		model.addAttribute("status", status != null ? status : 500);
		model.addAttribute("errorMessage", request.getAttribute(RequestDispatcher.ERROR_MESSAGE));
		model.addAttribute("errorRequestUri", requestUri);
		return "error";
	}
}
