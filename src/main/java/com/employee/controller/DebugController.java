package com.employee.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/debug")
public class DebugController {

	/**
	 * Minimal calendar page to isolate rendering: no layout, no glass, no spheres.
	 * If dates show here, the bug is CSS/layout on the Select Shifts page.
	 */
	@GetMapping("/calendar")
	public String calendar() {
		return "debug/calendar";
	}
}
