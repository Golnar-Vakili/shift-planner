package com.employee.controller;

import com.employee.domain.Employee;
import com.employee.repository.EmployeeRepository;
import com.employee.security.EmployeeUserDetails;
import com.employee.service.ProfileFileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfilePictureController {

	private final EmployeeRepository employeeRepository;
	private final ProfileFileService profileFileService;

	public ProfilePictureController(EmployeeRepository employeeRepository, ProfileFileService profileFileService) {
		this.employeeRepository = employeeRepository;
		this.profileFileService = profileFileService;
	}

	@GetMapping("/dashboard/profile")
	public String dashboardProfile(@AuthenticationPrincipal EmployeeUserDetails userDetails, Model model) {
		model.addAttribute("username", userDetails.getUsername());
		model.addAttribute("displayName", userDetails.getEmployee().getDisplayName() != null
				? userDetails.getEmployee().getDisplayName() : userDetails.getUsername());
		model.addAttribute("activePage", "profile");
		return "dashboard/profile";
	}

	@GetMapping("/admin/profile")
	public String adminProfile(@AuthenticationPrincipal EmployeeUserDetails userDetails, Model model) {
		model.addAttribute("username", userDetails.getUsername());
		model.addAttribute("displayName", userDetails.getEmployee().getDisplayName() != null
				? userDetails.getEmployee().getDisplayName() : userDetails.getUsername());
		model.addAttribute("activePage", "admin-profile");
		return "admin/profile";
	}

	@PostMapping("/dashboard/profile/upload")
	public String uploadDashboardProfilePicture(
			@AuthenticationPrincipal EmployeeUserDetails userDetails,
			@RequestParam("file") MultipartFile file,
			RedirectAttributes ra
	) {
		return handleUpload(userDetails, file, ra, "redirect:/dashboard/profile");
	}

	@PostMapping("/admin/profile/upload")
	public String uploadAdminProfilePicture(
			@AuthenticationPrincipal EmployeeUserDetails userDetails,
			@RequestParam("file") MultipartFile file,
			RedirectAttributes ra
	) {
		return handleUpload(userDetails, file, ra, "redirect:/admin/profile");
	}

	private String handleUpload(EmployeeUserDetails userDetails, MultipartFile file, RedirectAttributes ra, String redirect) {
		try {
			if (file == null || file.isEmpty()) {
				ra.addFlashAttribute("profileUploadError", "Please choose an image first.");
				return redirect;
			}
			String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
			if (!(contentType.contains("jpeg") || contentType.contains("png") || contentType.contains("webp"))) {
				ra.addFlashAttribute("profileUploadError", "Only JPG, PNG, or WEBP images are allowed.");
				return redirect;
			}

			Long id = userDetails.getEmployee().getId();
			String filename = profileFileService.storeProfileImage(id, file);

			Employee emp = userDetails.getEmployee();
			emp.setProfilePicturePath(filename);
			employeeRepository.save(emp);

			ra.addFlashAttribute("profileUploadSuccess", "Profile photo updated.");
			return redirect;
		} catch (Exception e) {
			ra.addFlashAttribute("profileUploadError", "Upload failed. Please try again.");
			return redirect;
		}
	}
}

