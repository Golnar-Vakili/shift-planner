package com.employee.controller;

import com.employee.security.EmployeeUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class CurrentUserAvatarAdvice {

	@ModelAttribute
	public void addProfilePictureUrl(
			@AuthenticationPrincipal EmployeeUserDetails userDetails,
			Model model
	) {
		if (userDetails == null || userDetails.getEmployee() == null) return;
		String path = userDetails.getEmployee().getProfilePicturePath();
		if (path == null || path.trim().isEmpty()) return;
		model.addAttribute("profilePictureUrl", "/user-photos/" + path);
	}
}

