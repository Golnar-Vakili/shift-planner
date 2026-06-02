package com.employee.service;

import com.employee.domain.*;
import com.employee.dto.VacationRequestDto;
import com.employee.dto.VacationDayDecisionDto;
import com.employee.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.Year;
import java.time.DayOfWeek;
import java.util.Comparator;

@Service
public class VacationRequestService {

	private final VacationRequestRepository vacationRequestRepository;
	private final VacationRequestDayRepository vacationRequestDayRepository;
	private final EmployeeRepository employeeRepository;
	private final ShiftPlanRepository shiftPlanRepository;
	private final ShiftEntryRepository shiftEntryRepository;

	public VacationRequestService(VacationRequestRepository vacationRequestRepository,
	                              VacationRequestDayRepository vacationRequestDayRepository,
	                              EmployeeRepository employeeRepository,
	                              ShiftPlanRepository shiftPlanRepository,
	                              ShiftEntryRepository shiftEntryRepository) {
		this.vacationRequestRepository = vacationRequestRepository;
		this.vacationRequestDayRepository = vacationRequestDayRepository;
		this.employeeRepository = employeeRepository;
		this.shiftPlanRepository = shiftPlanRepository;
		this.shiftEntryRepository = shiftEntryRepository;
	}

	/** Returns all dates in the given year on which the employee has at least one work shift (any plan status). */
	public Set<LocalDate> getDatesWithShifts(Long employeeId, int year) {
		LocalDate startOfYear = LocalDate.of(year, 1, 1);
		LocalDate endOfYear = LocalDate.of(year, 12, 31);
		List<ShiftPlan> plans = shiftPlanRepository.findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc(employeeId, startOfYear, endOfYear);
		Set<LocalDate> dates = new java.util.HashSet<>();
		for (ShiftPlan plan : plans) {
			List<ShiftEntry> entries = shiftEntryRepository.findByPlanIdAndEntryDateBetweenOrderByEntryDateAscStartTimeAsc(plan.getId(), startOfYear, endOfYear);
			entries.forEach(e -> dates.add(e.getEntryDate()));
		}
		return dates;
	}

	public Optional<VacationRequest> findByEmployeeIdAndYear(Long employeeId, int year) {
		return vacationRequestRepository.findByEmployeeIdAndYear(employeeId, year);
	}

	@Transactional
	public VacationRequest getOrCreateDraft(Long employeeId, int year) {
		return vacationRequestRepository.findByEmployeeIdAndYearIncludingDeleted(employeeId, year)
				.map(r -> {
					if (r.getDeletedAt() != null) {
						vacationRequestDayRepository.deleteByVacationRequestId(r.getId());
						r.setDeletedAt(null);
						r.setDeletedById(null);
						r.setStatus(VacationRequestStatus.DRAFT);
						r.setSubmittedAt(null);
						r.setReviewedAt(null);
						r.setReviewedById(null);
						r.setReviewComment(null);
						r.setRejectionReason(null);
						return vacationRequestRepository.save(r);
					}
					return r;
				})
				.orElseGet(() -> {
					VacationRequest req = new VacationRequest();
					req.setEmployeeId(employeeId);
					req.setYear(year);
					req.setStatus(VacationRequestStatus.DRAFT);
					return vacationRequestRepository.save(req);
				});
	}

	/** Employee may add/remove days in these states (incl. after admin date update or admin cancellation). */
	private static boolean canEmployeeEditVacationDays(VacationRequestStatus status) {
		return status == VacationRequestStatus.DRAFT
				|| status == VacationRequestStatus.REJECTED
				|| status == VacationRequestStatus.CHANGES_REQUESTED
				|| status == VacationRequestStatus.ADMIN_UPDATED
				|| status == VacationRequestStatus.ADMIN_CANCELLED;
	}

	/** Load request for the year; if none exists, return DTO with zero selected and full entitlement. */
	public VacationRequestDto getRequest(Long employeeId, int year) {
		Employee employee = employeeRepository.findById(employeeId).orElse(null);
		int entitlement = employee != null ? (employee.getAllowedVacationDays() != null ? employee.getAllowedVacationDays() : 20) : 20;

		Set<LocalDate> datesWithShifts = getDatesWithShifts(employeeId, year);
		List<LocalDate> selectedDates = new ArrayList<>();
		List<VacationDayDecisionDto> dayDecisions = new ArrayList<>();

		Optional<VacationRequest> opt = vacationRequestRepository.findByEmployeeIdAndYear(employeeId, year);
		if (opt.isPresent()) {
			List<VacationRequestDay> days = vacationRequestDayRepository.findByVacationRequestIdOrderByRequestDateAsc(opt.get().getId());
			selectedDates = days.stream().map(VacationRequestDay::getRequestDate).toList();
			dayDecisions = days.stream()
					.map(d -> new VacationDayDecisionDto(
							d.getRequestDate(),
							(d.getReviewStatus() != null ? d.getReviewStatus().name() : "PENDING_APPROVAL"),
							d.getReviewComment()
					))
					.toList();
		}

		int selectedCount = selectedDates.size();
		int remaining = Math.max(0, entitlement - selectedCount);
		String status = opt.map(r -> r.getStatus().name()).orElse(VacationRequestStatus.DRAFT.name());

		return new VacationRequestDto(
				year,
				entitlement,
				selectedCount,
				remaining,
				status,
				selectedDates,
				new ArrayList<>(datesWithShifts),
				dayDecisions
		);
	}

	/** Approved vacation dates for the given employee/year (per-day review status APPROVED). */
	public List<LocalDate> getApprovedVacationDates(Long employeeId, int year) {
		Optional<VacationRequest> opt = vacationRequestRepository.findByEmployeeIdAndYear(employeeId, year);
		if (opt.isEmpty()) return List.of();

		List<VacationRequestDay> days = vacationRequestDayRepository.findByVacationRequestIdOrderByRequestDateAsc(opt.get().getId());
		return days.stream()
				.filter(d -> d.getReviewStatus() == VacationDayReviewStatus.APPROVED)
				.map(VacationRequestDay::getRequestDate)
				.filter(java.util.Objects::nonNull)
				.sorted(Comparator.naturalOrder())
				.toList();
	}

	/** Add a vacation day. Validates: own request, DRAFT, not duplicate, not exceed entitlement, no shift conflict. */
	@Transactional
	public void addDay(Long employeeId, int year, LocalDate date) {
		Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new IllegalArgumentException("Employee not found"));
		int entitlement = employee.getAllowedVacationDays() != null ? employee.getAllowedVacationDays() : 20;

		VacationRequest request = getOrCreateDraft(employeeId, year);
		if (!canEmployeeEditVacationDays(request.getStatus())) {
			throw new IllegalArgumentException("You can only add or remove dates while the request is editable (draft, rejected, changes requested, after an admin date update, or after an admin cancellation). Current status: " + request.getStatus());
		}
		if (!request.getEmployeeId().equals(employeeId)) {
			throw new IllegalArgumentException("You can only modify your own vacation request.");
		}

		int currentYear = Year.now().getValue();
		if (year != currentYear) {
			throw new IllegalArgumentException("You can only select vacation dates for the current year (" + currentYear + ").");
		}
		if (date.isBefore(LocalDate.now())) {
			throw new IllegalArgumentException("Vacation cannot be in the past.");
		}
		if (date.getYear() != year) {
			throw new IllegalArgumentException("The date must be in year " + year + ".");
		}
		if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			throw new IllegalArgumentException("Sundays cannot be selected as vacation days.");
		}

		if (vacationRequestDayRepository.existsByVacationRequestIdAndRequestDate(request.getId(), date)) {
			throw new IllegalArgumentException("This date is already selected.");
		}

		long currentCount = vacationRequestDayRepository.findByVacationRequestIdOrderByRequestDateAsc(request.getId()).size();
		if (currentCount >= entitlement) {
			throw new IllegalArgumentException("You cannot select more than your entitlement (" + entitlement + " days).");
		}

		Set<LocalDate> withShifts = getDatesWithShifts(employeeId, year);
		if (withShifts.contains(date)) {
			throw new IllegalArgumentException("You already have a work shift on this date and cannot request vacation here.");
		}

		VacationRequestDay day = new VacationRequestDay();
		day.setVacationRequestId(request.getId());
		day.setRequestDate(date);
		vacationRequestDayRepository.save(day);
	}

	/** Remove a vacation day. Validates: own request, editable state. */
	@Transactional
	public void removeDay(Long employeeId, int year, LocalDate date) {
		VacationRequest request = vacationRequestRepository.findByEmployeeIdAndYear(employeeId, year)
				.orElseThrow(() -> new IllegalArgumentException("No vacation request found for this year."));
		if (!canEmployeeEditVacationDays(request.getStatus())) {
			throw new IllegalArgumentException("You can only add or remove dates while the request is editable (draft, rejected, changes requested, after an admin date update, or after an admin cancellation). Current status: " + request.getStatus());
		}
		if (!request.getEmployeeId().equals(employeeId)) {
			throw new IllegalArgumentException("You can only modify your own vacation request.");
		}
		vacationRequestDayRepository.deleteByVacationRequestIdAndRequestDate(request.getId(), date);
	}

	/** Submit the request. Validates: selected count exactly equals entitlement. */
	@Transactional
	public void submit(Long employeeId, int year) {
		Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new IllegalArgumentException("Employee not found"));
		int entitlement = employee.getAllowedVacationDays() != null ? employee.getAllowedVacationDays() : 20;

		VacationRequest request = vacationRequestRepository.findByEmployeeIdAndYear(employeeId, year)
				.orElseThrow(() -> new IllegalArgumentException("No vacation request found for this year."));
		if (!canEmployeeEditVacationDays(request.getStatus())) {
			throw new IllegalArgumentException("Only an editable request can be submitted (draft, rejected, changes requested, after an admin date update, or after an admin cancellation). Current status: " + request.getStatus());
		}
		if (!request.getEmployeeId().equals(employeeId)) {
			throw new IllegalArgumentException("You can only submit your own vacation request.");
		}

		long selectedCount = vacationRequestDayRepository.findByVacationRequestIdOrderByRequestDateAsc(request.getId()).size();
		if (selectedCount < entitlement) {
			throw new IllegalArgumentException("You must select exactly " + entitlement + " vacation days before submitting. Currently selected: " + selectedCount + ".");
		}
		if (selectedCount > entitlement) {
			throw new IllegalArgumentException("You cannot select more than your entitlement (" + entitlement + " days). Currently selected: " + selectedCount + ".");
		}

		int currentYear = Year.now().getValue();
		List<VacationRequestDay> days = vacationRequestDayRepository.findByVacationRequestIdOrderByRequestDateAsc(request.getId());
		for (VacationRequestDay day : days) {
			if (day.getRequestDate().isBefore(LocalDate.now())) {
				throw new IllegalArgumentException("Vacation request contains a past date. Please remove past dates and select only current-year dates.");
			}
			if (day.getRequestDate().getYear() != currentYear) {
				throw new IllegalArgumentException("Vacation request may only contain dates for the current year (" + currentYear + ").");
			}
			if (day.getRequestDate().getDayOfWeek() == DayOfWeek.SUNDAY) {
				throw new IllegalArgumentException("Sundays cannot be selected as vacation days. Please remove any Sunday from your selection.");
			}
		}

		request.setStatus(VacationRequestStatus.PENDING);
		request.setSubmittedAt(java.time.Instant.now());
		request.setRejectionReason(null);
		request.setReviewComment(null);
		vacationRequestRepository.save(request);
	}
}
