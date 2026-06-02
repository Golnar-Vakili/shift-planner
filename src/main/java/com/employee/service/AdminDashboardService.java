package com.employee.service;

import com.employee.domain.ShiftEntry;
import com.employee.domain.ShiftPlan;
import com.employee.domain.ShiftPlanStatus;
import com.employee.domain.VacationRequestStatus;
import com.employee.dto.AdminDashboardOverviewDto;
import com.employee.repository.EmployeeRepository;
import com.employee.repository.ShiftEntryRepository;
import com.employee.repository.ShiftPlanRepository;
import com.employee.repository.VacationRequestRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminDashboardService {

	private final VacationRequestRepository vacationRequestRepository;
	private final ShiftPlanRepository shiftPlanRepository;
	private final ShiftEntryRepository shiftEntryRepository;
	private final EmployeeRepository employeeRepository;

	public AdminDashboardService(VacationRequestRepository vacationRequestRepository,
	                             ShiftPlanRepository shiftPlanRepository,
	                             ShiftEntryRepository shiftEntryRepository,
	                             EmployeeRepository employeeRepository) {
		this.vacationRequestRepository = vacationRequestRepository;
		this.shiftPlanRepository = shiftPlanRepository;
		this.shiftEntryRepository = shiftEntryRepository;
		this.employeeRepository = employeeRepository;
	}

	public AdminDashboardOverviewDto getOverview() {
		long pendingVacation = vacationRequestRepository.countByStatus(VacationRequestStatus.PENDING);
		long pendingShiftPlans = shiftPlanRepository.countByStatus(ShiftPlanStatus.PENDING_APPROVAL);

		WorkforceStatus workforce = computeWorkforceStatus();
		long totalEmployees = employeeRepository.count();

		return new AdminDashboardOverviewDto(
				pendingVacation,
				pendingShiftPlans,
				workforce.activeShiftLabel,
				workforce.activeEmployeesCount,
				totalEmployees
		);
	}

	private WorkforceStatus computeWorkforceStatus() {
		LocalDate today = LocalDate.now();
		LocalDate yesterday = today.minusDays(1);
		LocalTime now = LocalTime.now();

		boolean isNight = now.isBefore(LocalTime.of(6, 0)) || !now.isBefore(LocalTime.of(22, 0));
		String label = isNight ? "Night shift" : "Day shift";

		// Load approved plans that overlap yesterday..today (covers overnight shifts).
		List<ShiftPlan> plans = shiftPlanRepository
				.findByStatusAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByPeriodStartAsc(
						ShiftPlanStatus.APPROVED, today, yesterday);

		Set<Long> activeEmployees = new HashSet<>();
		for (ShiftPlan plan : plans) {
			List<ShiftEntry> entries = shiftEntryRepository.findByPlanIdAndEntryDateBetweenOrderByEntryDateAscStartTimeAsc(
					plan.getId(), yesterday, today);
			for (ShiftEntry e : entries) {
				if (e == null || e.getEntryDate() == null || e.getStartTime() == null || e.getEndTime() == null) continue;
				if (isEntryActiveNow(e, now, today, yesterday)) {
					activeEmployees.add(plan.getUserId());
				}
			}
		}

		return new WorkforceStatus(label, activeEmployees.size());
	}

	private static boolean isEntryActiveNow(ShiftEntry e, LocalTime now, LocalDate today, LocalDate yesterday) {
		LocalDate d = e.getEntryDate();
		LocalTime start = e.getStartTime();
		LocalTime end = e.getEndTime();
		boolean overnight = start != null && end != null && !end.isAfter(start);

		// Entry on today's date:
		if (today.equals(d)) {
			if (!overnight) {
				return (now.equals(start) || now.isAfter(start)) && now.isBefore(end);
			}
			// Overnight shift starting today: active in late hours of today.
			return now.equals(start) || now.isAfter(start);
		}

		// Entry on yesterday's date that runs overnight into today: active in early hours of today.
		if (yesterday.equals(d) && overnight) {
			return now.isBefore(end);
		}

		return false;
	}

	private record WorkforceStatus(String activeShiftLabel, long activeEmployeesCount) {}
}

