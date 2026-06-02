package com.employee.controller;

import com.employee.repository.EmployeeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/admin/api/overtime")
public class AdminOvertimeEmployeeApiController {

	private final EmployeeRepository employeeRepository;

	public AdminOvertimeEmployeeApiController(EmployeeRepository employeeRepository) {
		this.employeeRepository = employeeRepository;
	}

	public record EmployeeSearchResult(Long id, String name, String avatarUrl) {}

	@GetMapping("/employees/search")
	public List<EmployeeSearchResult> search(@RequestParam(name = "q", required = false) String query) {
		String q = query == null ? "" : query.trim();
		if (q.isEmpty()) return List.of();
		if (q.length() < 2) return List.of();

		return employeeRepository.searchByDisplayNameOrUsername(q, PageRequest.of(0, 12))
				.stream()
				.map(e -> new EmployeeSearchResult(
						e.getId(),
						(e.getDisplayName() != null && !e.getDisplayName().isBlank()) ? e.getDisplayName() : e.getUsername(),
						toAvatarUrl(e.getProfilePicturePath())
				))
				.toList();
	}

	private static String toAvatarUrl(String profilePicturePath) {
		if (profilePicturePath == null) return null;
		String p = profilePicturePath.trim();
		if (p.isEmpty()) return null;
		return "/user-photos/" + p;
	}
}

