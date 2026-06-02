package com.employee.controller;

import com.employee.domain.ContractType;
import com.employee.dto.ContractOverviewDto;
import com.employee.dto.VacationRequestDto;
import com.employee.security.EmployeeUserDetails;
import com.employee.service.ShiftPlanService;
import com.employee.service.VacationRequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

	private final ShiftPlanService shiftPlanService;
	private final VacationRequestService vacationRequestService;

	public DashboardController(ShiftPlanService shiftPlanService, VacationRequestService vacationRequestService) {
		this.shiftPlanService = shiftPlanService;
		this.vacationRequestService = vacationRequestService;
	}

	@GetMapping
	public String home(@AuthenticationPrincipal EmployeeUserDetails userDetails, Model model) {
		String username = userDetails.getUsername();
		String displayName = userDetails.getEmployee().getDisplayName() != null
				? userDetails.getEmployee().getDisplayName()
				: username;
		Long employeeId = userDetails.getEmployee().getId();
		YearMonth currentMonth = YearMonth.now();
		YearMonth nextMonth = currentMonth.plusMonths(1);
		int year = Year.now().getValue();

		model.addAttribute("username", username);
		model.addAttribute("displayName", displayName);
		model.addAttribute("currentMonth", currentMonth);
		model.addAttribute("nextMonth", nextMonth);
		model.addAttribute("shiftsCurrentMonth", shiftPlanService.getApprovedShiftSummaryLines(employeeId, currentMonth));
		model.addAttribute("shiftsNextMonth", shiftPlanService.getApprovedShiftSummaryLines(employeeId, nextMonth));
		model.addAttribute("planStatus", shiftPlanService.getCurrentPlanStatus(employeeId));
		model.addAttribute("thisWeekPlannedHours", shiftPlanService.getPlannedHoursThisWeek(employeeId));

		VacationRequestDto vacation = vacationRequestService.getRequest(employeeId, year);
		model.addAttribute("vacationSummary", vacation);
		model.addAttribute("approvedVacations", vacationRequestService.getApprovedVacationDates(employeeId, year));
		List<String> rejectedHolidaysThisYear = new ArrayList<>();
		if ("REJECTED".equalsIgnoreCase(vacation.status())) {
			rejectedHolidaysThisYear.add("Your vacation request for " + year + " was rejected. You can submit a new request.");
		}
		model.addAttribute("rejectedHolidaysThisYear", rejectedHolidaysThisYear);

		shiftPlanService.getLatestApprovedPlanMessage(employeeId)
				.ifPresent(msg -> model.addAttribute("shiftPlanApprovedMessage", msg));
		model.addAttribute("activePage", "home");
		return "dashboard/home";
	}

	@GetMapping("/shifts")
	public String shifts(@AuthenticationPrincipal EmployeeUserDetails userDetails, Model model) {
		model.addAttribute("username", userDetails.getUsername());
		model.addAttribute("displayName", userDetails.getEmployee().getDisplayName() != null
				? userDetails.getEmployee().getDisplayName() : userDetails.getUsername());
		model.addAttribute("activePage", "shifts");
		return "dashboard/shifts";
	}

	@GetMapping("/holidays")
	public String holidays(@AuthenticationPrincipal EmployeeUserDetails userDetails, Model model) {
		model.addAttribute("username", userDetails.getUsername());
		model.addAttribute("displayName", userDetails.getEmployee().getDisplayName() != null
				? userDetails.getEmployee().getDisplayName() : userDetails.getUsername());
		model.addAttribute("activePage", "holidays");
		return "dashboard/holidays";
	}

	@GetMapping("/contract")
	public String contract(@AuthenticationPrincipal EmployeeUserDetails userDetails, Model model) {
		model.addAttribute("username", userDetails.getUsername());
		model.addAttribute("displayName", userDetails.getEmployee().getDisplayName() != null
				? userDetails.getEmployee().getDisplayName() : userDetails.getUsername());
		model.addAttribute("activePage", "contract");

		var employee = userDetails.getEmployee();
		ContractType ct = employee.getContractType() != null ? employee.getContractType() : ContractType.FULL_TIME;
		String contractTypeLabel = switch (ct) {
			case FULL_TIME -> "Full-time";
			case PART_TIME -> "Part-time";
			case MINI_JOB -> "Mini-job";
		};
		String employmentModelLabel = switch (ct) {
			case FULL_TIME -> "Vollzeit";
			case PART_TIME -> "Teilzeit";
			case MINI_JOB -> "Minijob";
		};
		BigDecimal week = employee.getAllowedHoursPerWeek();
		BigDecimal month = employee.getAllowedHoursPerMonth();
		String hoursPerWeek = week != null ? formatHours(week) : "—";
		String hoursPerMonth = month != null ? formatHours(month) : null;
		int vacationDays = employee.getAllowedVacationDays() != null ? employee.getAllowedVacationDays() : 20;

		LocalDate windowStart = shiftPlanService.getAllowedWindowStart();
		LocalDate windowEnd = shiftPlanService.getAllowedWindowEnd();
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy");
		ContractOverviewDto overview = new ContractOverviewDto(
				contractTypeLabel,
				hoursPerWeek,
				hoursPerMonth,
				vacationDays,
				windowStart.format(fmt),
				windowEnd.format(fmt),
				employmentModelLabel
		);
		model.addAttribute("contractOverview", overview);
		model.addAttribute("contractType", ct);
		return "dashboard/contract";
	}

	private static String formatHours(BigDecimal h) {
		if (h == null) return "—";
		if (h.stripTrailingZeros().scale() <= 0) return h.toPlainString();
		return h.setScale(1, java.math.RoundingMode.HALF_UP).toPlainString();
	}
}
