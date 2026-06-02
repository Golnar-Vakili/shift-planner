package com.employee.service;

import com.employee.config.ShiftPresetConfig;
import com.employee.domain.*;
import com.employee.dto.*;
import com.employee.repository.ShiftEntryRepository;
import com.employee.repository.ShiftPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

@Service
public class ShiftPlanService {

	private static final Logger log = LoggerFactory.getLogger(ShiftPlanService.class);

	/** Days to wait before the first selectable date (e.g. 21 = 3 weeks). Configurable via shift-planning.delay-days. */
	@Value("${shift-planning.delay-days:21}")
	private int delayDays;

	/** Number of days in the planable range after the delay. Configurable via shift-planning.window-days. */
	@Value("${shift-planning.window-days:21}")
	private int windowDays;

	private static final double MIN_HOURS_RATIO = 0.8;
	private static final int TOLERANCE_HOURS = 2;

	private final ShiftPlanRepository planRepository;
	private final ShiftEntryRepository entryRepository;
	private final HolidayService holidayService;
	private final ShiftPresetConfig presetConfig;

	public ShiftPlanService(ShiftPlanRepository planRepository,
	                        ShiftEntryRepository entryRepository,
	                        HolidayService holidayService,
	                        ShiftPresetConfig presetConfig) {
		this.planRepository = planRepository;
		this.entryRepository = entryRepository;
		this.holidayService = holidayService;
		this.presetConfig = presetConfig;
	}

	/** First selectable date = today + delayDays (e.g. 3 weeks from today). */
	public LocalDate getAllowedWindowStart() {
		return LocalDate.now().plusDays(delayDays);
	}

	public LocalDate getAllowedWindowEnd() {
		return getAllowedWindowStart().plusDays(windowDays - 1);
	}

	public boolean isDateInWindow(LocalDate date) {
		return !date.isBefore(getAllowedWindowStart()) && !date.isAfter(getAllowedWindowEnd());
	}

	public boolean isHoliday(LocalDate date) {
		return holidayService.getHolidays(date.getYear(), "DE").stream()
				.anyMatch(h -> h.date().equals(date));
	}

	public ShiftWindowDto getWindow(Long userId, Employee employee) {
		// TEMP DEBUG: visible even if logger is filtered
		System.out.println("[SHIFT WINDOW] getWindow called userId=" + userId);
		LocalDate start = getAllowedWindowStart();
		LocalDate end = getAllowedWindowEnd();
		// Use overlap query so approved plans that overlap the window are included (keeps dashboard + shifts page consistent).
		List<ShiftPlan> windowPlans = planRepository
				.findByUserIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByPeriodStartAsc(userId, end, start);
		List<ShiftEntryDto> entries = new ArrayList<>();
		String planStatus = null;
		// [SHIFT DEBUG]
		log.info("[SHIFT DEBUG] userId={} windowStart={} windowEnd={}", userId, start, end);
		log.info("[SHIFT DEBUG] windowPlans count={}", windowPlans.size());
		List<ShiftPlan> pendingByStatus = planRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, ShiftPlanStatus.PENDING_APPROVAL);
		java.util.Set<Long> windowPlanIds = new java.util.HashSet<>(windowPlans.stream().map(ShiftPlan::getId).toList());
		List<ShiftPlan> oldPendingPlans = pendingByStatus.stream()
				.filter(p -> !windowPlanIds.contains(p.getId()))
				.toList();
		log.info("[SHIFT DEBUG] oldPendingPlans count={}", oldPendingPlans.size());
		for (ShiftPlan plan : pendingByStatus) {
			log.info("[SHIFT DEBUG] plan id={} status={} periodStart={} periodEnd={}", plan.getId(), plan.getStatus(), plan.getPeriodStart(), plan.getPeriodEnd());
		}
		for (ShiftPlan plan : oldPendingPlans) {
			log.info("[SHIFT DEBUG] oldPending plan id={} status={} periodStart={} periodEnd={}", plan.getId(), plan.getStatus(), plan.getPeriodStart(), plan.getPeriodEnd());
		}
		// Include a read-only lookback so users can still see approved shifts shortly before the window.
		LocalDate approvedReadOnlyStart = start.minusDays(45);

		for (ShiftPlan plan : windowPlans) {
			String planStatusStr = plan.getStatus().name();
			if (planStatus == null) planStatus = planStatusStr;
			else if (plan.getStatus() == ShiftPlanStatus.DRAFT) planStatus = "DRAFT";
			else if (plan.getStatus() == ShiftPlanStatus.PENDING_APPROVAL && !"DRAFT".equals(planStatus)) planStatus = "PENDING_APPROVAL";
			else if (plan.getStatus() == ShiftPlanStatus.APPROVED && !"DRAFT".equals(planStatus) && !"PENDING_APPROVAL".equals(planStatus)) planStatus = "APPROVED";
			else if (plan.getStatus() == ShiftPlanStatus.REJECTED && !"DRAFT".equals(planStatus) && !"PENDING_APPROVAL".equals(planStatus) && !"APPROVED".equals(planStatus)) planStatus = "REJECTED";
			// For APPROVED plans, also include entries slightly before the window as read-only.
			LocalDate queryStart = (plan.getStatus() == ShiftPlanStatus.APPROVED) ? approvedReadOnlyStart : start;
			entryRepository.findByPlanIdAndEntryDateBetweenOrderByEntryDateAscStartTimeAsc(plan.getId(), queryStart, end)
					.forEach(e -> {
						String entryStatusStr;
						String comment = e.getReviewComment();
						boolean editable = false;
						boolean deletable = false;
						boolean readOnly = false;

						if (plan.getStatus() == ShiftPlanStatus.DRAFT) {
							entryStatusStr = "DRAFT";
							editable = true;
							deletable = true;
						} else if (plan.getStatus() == ShiftPlanStatus.REJECTED) {
							ShiftEntryReviewStatus rs = e.getReviewStatus();
							entryStatusStr = (rs != null ? rs.name() : "REJECTED");
							editable = true;
							deletable = true;
						} else if (plan.getStatus() == ShiftPlanStatus.APPROVED) {
							ShiftEntryReviewStatus rs = e.getReviewStatus();
							entryStatusStr = (rs != null ? rs.name() : "APPROVED");
							// Users cannot change approved shifts unless admin explicitly requests a change again.
							editable = (rs == ShiftEntryReviewStatus.REJECTED || rs == ShiftEntryReviewStatus.CHANGES_REQUESTED);
							deletable = editable;
							// Entries before the selectable window are visible but not editable.
							readOnly = e.getEntryDate() != null && e.getEntryDate().isBefore(start);
							// Admin-cancelled shifts should not appear in the employee calendar.
							if (rs == ShiftEntryReviewStatus.ADMIN_CANCELLED) {
								return;
							}
						} else {
							ShiftEntryReviewStatus rs = e.getReviewStatus();
							entryStatusStr = (rs != null ? rs.name() : "PENDING_APPROVAL");
							editable = (rs == ShiftEntryReviewStatus.REJECTED || rs == ShiftEntryReviewStatus.CHANGES_REQUESTED);
							deletable = editable;
						}

						entries.add(ShiftEntryDto.ofWindow(
								e.getId(), e.getEntryDate(), e.getStartTime(), e.getEndTime(), e.getShiftType(), e.getMinutes(),
								planStatusStr, entryStatusStr, comment,
								editable, deletable, readOnly
						));
					});
		}
		// Also load APPROVED plans outside windowPlans (overlapping lookback range) as read-only, to cover older approved plans.
		List<ShiftPlan> approvedOutside = planRepository
				.findByUserIdAndStatusAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByPeriodStartAsc(
						userId, ShiftPlanStatus.APPROVED, end, approvedReadOnlyStart);
		// Avoid duplicates if an entry is already in the list (same date + times).
		Set<String> existingKeys = new HashSet<>();
		for (ShiftEntryDto dto : entries) {
			if (dto == null || dto.date() == null || dto.startTime() == null || dto.endTime() == null) continue;
			existingKeys.add(dto.date() + "|" + dto.startTime() + "|" + dto.endTime());
		}
		for (ShiftPlan plan : approvedOutside) {
			// Skip plans already in windowPlans; those entries already loaded above.
			if (windowPlanIds.contains(plan.getId())) continue;
			entryRepository.findByPlanIdAndEntryDateBetweenOrderByEntryDateAscStartTimeAsc(plan.getId(), approvedReadOnlyStart, end)
					.forEach(e -> {
						if (e.getReviewStatus() == ShiftEntryReviewStatus.ADMIN_CANCELLED) return;
						String key = e.getEntryDate() + "|" + e.getStartTime() + "|" + e.getEndTime();
						if (!existingKeys.add(key)) return;
						entries.add(ShiftEntryDto.ofWindow(
								e.getId(), e.getEntryDate(), e.getStartTime(), e.getEndTime(), e.getShiftType(), e.getMinutes(),
								ShiftPlanStatus.APPROVED.name(), "APPROVED", e.getReviewComment(),
								false, false, true
						));
					});
		}
		// Debug: confirm read-only approved entries are included (e.g., early-month approved shifts).
		try {
			long approvedReadOnlyCount = entries.stream().filter(e -> "APPROVED".equals(e.entryStatus()) && Boolean.TRUE.equals(e.readOnly())).count();
			var firstApproved = entries.stream().filter(e -> "APPROVED".equals(e.entryStatus()))
					.map(ShiftEntryDto::date).min(LocalDate::compareTo).orElse(null);
			var lastApproved = entries.stream().filter(e -> "APPROVED".equals(e.entryStatus()))
					.map(ShiftEntryDto::date).max(LocalDate::compareTo).orElse(null);
			boolean hasApr7 = entries.stream().anyMatch(e -> e.date() != null && e.date().toString().equals("2026-04-07") && "APPROVED".equals(e.entryStatus()));
			log.info("[SHIFT DEBUG] approvedReadOnlyStart={} approvedOutsidePlans={} approvedReadOnlyEntries={} approvedDateRange={}..{} hasApr7={}",
					approvedReadOnlyStart, approvedOutside.size(), approvedReadOnlyCount, firstApproved, lastApproved, hasApr7);
		} catch (Exception ignore) {
			// no-op
		}
		// Also load PENDING_APPROVAL plans outside the current window so user can see them (read-only).
		for (ShiftPlan plan : oldPendingPlans) {
			entryRepository.findByPlanIdAndEntryDateBetweenOrderByEntryDateAscStartTimeAsc(plan.getId(), plan.getPeriodStart(), plan.getPeriodEnd())
					.forEach(e -> entries.add(ShiftEntryDto.ofWindow(
							e.getId(), e.getEntryDate(), e.getStartTime(), e.getEndTime(), e.getShiftType(), e.getMinutes(),
							plan.getStatus().name(),
							(e.getReviewStatus() != null ? e.getReviewStatus().name() : "PENDING_APPROVAL"),
							e.getReviewComment(),
							false, false, true
					)));
		}
		entries.sort(Comparator.comparing(ShiftEntryDto::date).thenComparing(ShiftEntryDto::startTime));

		ContractInfoDto contractInfo = buildContractInfo(employee, entries);
		List<HolidayDto> holidays = holidayService.getHolidays(start.getYear(), "DE").stream()
				.filter(h -> !h.date().isBefore(start) && !h.date().isAfter(end))
				.toList();
		if (planStatus == null && !windowPlans.isEmpty()) {
			planStatus = windowPlans.get(0).getStatus().name();
		}
		return new ShiftWindowDto(start, end, entries, contractInfo, holidays, planStatus);
	}

	private ContractInfoDto buildContractInfo(Employee employee, List<ShiftEntryDto> entries) {
		return buildContractInfo(employee, entries, getAllowedWindowStart(), getAllowedWindowEnd());
	}

	private ContractInfoDto buildContractInfo(Employee employee, List<ShiftEntryDto> entries, LocalDate start, LocalDate end) {
		ContractType ct = employee.getContractType();
		String label = switch (ct != null ? ct : ContractType.FULL_TIME) {
			case FULL_TIME -> "Vollzeit";
			case PART_TIME -> "Teilzeit";
			case MINI_JOB -> "Minijob";
		};
		BigDecimal requiredWeek = employee.getAllowedHoursPerWeek() != null ? employee.getAllowedHoursPerWeek() : BigDecimal.valueOf(40);
		BigDecimal requiredMonth = employee.getAllowedHoursPerMonth();
		String rule = requiredMonth != null
				? "~" + requiredWeek + " h/Woche, ~" + requiredMonth + " h/Monat"
				: "~" + requiredWeek + " h/Woche";

		WeekFields wf = WeekFields.of(Locale.GERMANY);
		List<WeekSummaryDto> summaries = new ArrayList<>();
		for (LocalDate w = start; !w.isAfter(end); w = w.plusWeeks(1)) {
			LocalDate weekStart = w;
			LocalDate weekEnd = w.plusDays(6);
			double selected = entries.stream()
					.filter(e -> !e.date().isBefore(weekStart) && !e.date().isAfter(weekEnd))
					.mapToInt(ShiftEntryDto::minutes)
					.sum() / 60.0;
			BigDecimal req = requiredWeek;
			boolean valid = selected >= req.doubleValue() * MIN_HOURS_RATIO && selected <= req.doubleValue() + TOLERANCE_HOURS;
			summaries.add(new WeekSummaryDto(weekStart, BigDecimal.valueOf(selected).setScale(1, RoundingMode.HALF_UP), req, valid));
		}
		return new ContractInfoDto(label, requiredWeek, requiredMonth, rule, summaries);
	}

	@Transactional
	public ShiftEntryDto addEntry(Long userId, AddShiftRequest request) {
		if (isHoliday(request.date())) {
			throw new IllegalArgumentException("Shifts cannot be assigned on public holidays.");
		}
		if (request.date().getDayOfWeek() == DayOfWeek.SUNDAY) {
			throw new IllegalArgumentException("Shifts cannot be planned on Sundays.");
		}
		if (!isDateInWindow(request.date())) {
			throw new IllegalArgumentException("You can only plan shifts starting " + delayDays +
			 " days from today (earliest date: " + getAllowedWindowStart() +
			 "). The selected date is outside the allowed planning window.");
		}
		// Do not allow adding entries only while a plan is pending approval (approved plans are editable).
		LocalDate windowStart = getAllowedWindowStart();
		LocalDate windowEnd = getAllowedWindowEnd();
		List<ShiftPlan> existing = planRepository.findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc(userId, windowStart, windowEnd);
		boolean hasLocked = existing.stream().anyMatch(p -> p.getStatus() == ShiftPlanStatus.PENDING_APPROVAL);
		if (hasLocked) {
			throw new IllegalArgumentException("You cannot add new shifts while your plan is pending approval.");
		}
		ShiftPlan plan = getOrCreateDraft(userId);
		LocalTime start;
		LocalTime end;
		int minutes;
		ShiftType type = request.shiftType() != null ? request.shiftType() : ShiftType.MORNING;
		switch (type) {
			case MORNING -> {
				start = presetConfig.getMorningStart();
				end = presetConfig.getMorningEnd();
				minutes = (int) java.time.Duration.between(start, end).toMinutes();
			}
			case NIGHT -> {
				start = presetConfig.getNightStart();
				end = presetConfig.getNightEnd();
				minutes = 480;
			}
			case CUSTOM -> {
				start = request.startTime() != null ? LocalTime.parse(request.startTime()) : LocalTime.of(8, 0);
				end = request.endTime() != null ? LocalTime.parse(request.endTime()) : LocalTime.of(16, 0);
				if (!end.isAfter(start)) end = end.plusHours(24);
				minutes = (int) java.time.Duration.between(start, end).toMinutes();
				if (minutes < 120 || minutes > 720) throw new IllegalArgumentException("Custom shift must be between 2 and 12 hours.");
			}
			default -> throw new IllegalArgumentException("Unknown shift type.");
		}
		ShiftEntry entry = new ShiftEntry();
		entry.setPlanId(plan.getId());
		entry.setEntryDate(request.date());
		entry.setStartTime(start);
		entry.setEndTime(end);
		entry.setShiftType(type);
		entry.setMinutes(minutes);
		entry = entryRepository.save(entry);
		return ShiftEntryDto.ofDraft(entry.getId(), entry.getEntryDate(), entry.getStartTime(), entry.getEndTime(), entry.getShiftType(), entry.getMinutes());
	}

	@Transactional
	public void deleteEntry(Long userId, Long entryId) {
		ShiftEntry entry = entryRepository.findById(entryId).orElse(null);
		if (entry == null) return;
		ShiftPlan plan = planRepository.findById(entry.getPlanId()).orElse(null);
		if (plan == null || !plan.getUserId().equals(userId)) {
			throw new IllegalArgumentException("Entry cannot be removed.");
		}
		// Deletion rules:
		// - Draft plans: allowed
		// - Pending approval: only REJECTED / CHANGES_REQUESTED entries can be removed
		// - Fully rejected plans: allowed (user can edit/resubmit)
		ShiftEntryReviewStatus rs = entry.getReviewStatus();
		boolean entryEditableByReview = (rs == ShiftEntryReviewStatus.REJECTED || rs == ShiftEntryReviewStatus.CHANGES_REQUESTED);
		if (plan.getStatus() == ShiftPlanStatus.APPROVED) {
			throw new IllegalArgumentException("Approved shifts cannot be removed.");
		}
		if (plan.getStatus() == ShiftPlanStatus.PENDING_APPROVAL && !entryEditableByReview) {
			throw new IllegalArgumentException("This shift is pending or approved and cannot be removed until reviewed.");
		}
		entryRepository.delete(entry);
	}

	private ShiftPlan getOrCreateDraft(Long userId) {
		return getOrCreateDraft(userId, getAllowedWindowStart(), getAllowedWindowEnd());
	}

	private ShiftPlan getOrCreateDraft(Long userId, LocalDate start, LocalDate end) {
		List<ShiftPlan> existing = planRepository.findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc(userId, start, end);
		Optional<ShiftPlan> draft = existing.stream().filter(p -> p.getStatus() == ShiftPlanStatus.DRAFT).findFirst();
		if (draft.isPresent()) return draft.get();
		// Reuse REJECTED plan for resubmission; do NOT reuse APPROVED (prevents overwriting approved data).
		Optional<ShiftPlan> rejected = existing.stream().filter(p -> p.getStatus() == ShiftPlanStatus.REJECTED).findFirst();
		if (rejected.isPresent()) return rejected.get();
		ShiftPlan plan = new ShiftPlan();
		plan.setUserId(userId);
		plan.setPeriodStart(start);
		plan.setPeriodEnd(end);
		plan.setStatus(ShiftPlanStatus.DRAFT);
		return planRepository.save(plan);
	}

	@Transactional
	public void saveDraft(Long userId, SaveShiftsRequest request) {
		LocalDate start = getAllowedWindowStart();
		LocalDate end = getAllowedWindowEnd();
		List<ShiftEntryDto> entries = request.entries() != null ? request.entries() : List.<ShiftEntryDto>of();
		for (ShiftEntryDto dto : entries) {
			if (dto.date().getDayOfWeek() == DayOfWeek.SUNDAY) {
				throw new IllegalArgumentException("Shifts cannot be planned on Sundays. Please remove any Sunday from your plan.");
			}
			if (dto.date().isBefore(start) || dto.date().isAfter(end)) {
				throw new IllegalArgumentException("All shift dates must be within the planning window (from " + start + " to " + end + "). Found date outside range: " + dto.date() + ".");
			}
		}
		// Do not allow saving a draft only while a plan is pending approval (approved plans are editable).
		List<ShiftPlan> existing = planRepository.findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc(userId, start, end);
		boolean hasLocked = existing.stream().anyMatch(p -> p.getStatus() == ShiftPlanStatus.PENDING_APPROVAL);
		if (hasLocked) {
			throw new IllegalArgumentException("You cannot save a draft while a shift plan is pending approval.");
		}
		ShiftPlan plan = existing.stream().filter(p -> p.getStatus() == ShiftPlanStatus.DRAFT).findFirst()
				.or(() -> existing.stream().filter(p -> p.getStatus() == ShiftPlanStatus.REJECTED).findFirst())
				.orElseGet(() -> {
					ShiftPlan p = new ShiftPlan();
					p.setUserId(userId);
					p.setPeriodStart(start);
					p.setPeriodEnd(end);
					p.setStatus(ShiftPlanStatus.DRAFT);
					return planRepository.save(p);
				});
		plan.setStatus(ShiftPlanStatus.DRAFT);
		plan.setRejectionReason(null);
		planRepository.save(plan);
		entryRepository.deleteByPlanId(plan.getId());
		for (ShiftEntryDto dto : entries) {
			if (dto.date().isBefore(start) || dto.date().isAfter(end) || isHoliday(dto.date()) || dto.date().getDayOfWeek() == DayOfWeek.SUNDAY) continue;
			ShiftEntry e = new ShiftEntry();
			e.setPlanId(plan.getId());
			e.setEntryDate(dto.date());
			e.setStartTime(LocalTime.parse(dto.startTime()));
			e.setEndTime(LocalTime.parse(dto.endTime()));
			e.setShiftType(dto.shiftType());
			e.setMinutes(dto.minutes());
			e.setReviewStatus(null);
			e.setReviewComment(null);
			e.setReviewedAt(null);
			e.setReviewedById(null);
			entryRepository.save(e);
		}
	}

	@Transactional
	public void submit(Long userId, SaveShiftsRequest request, Employee employee) {
		LocalDate start = request.windowStart() != null && request.windowEnd() != null
				? request.windowStart()
				: getAllowedWindowStart();
		LocalDate end = request.windowStart() != null && request.windowEnd() != null
				? request.windowEnd()
				: getAllowedWindowEnd();
		List<ShiftEntryDto> entries = request.entries() != null ? request.entries() : List.of();
		log.debug("Submit shift plan: userId={}, window={} to {}, entriesCount={}", userId, start, end, entries.size());
		for (ShiftEntryDto dto : entries) {
			if (dto.date().getDayOfWeek() == DayOfWeek.SUNDAY) {
				throw new IllegalArgumentException("Shifts cannot be planned on Sundays. Please remove any Sunday from your plan.");
			}
			if (dto.date().isBefore(start) || dto.date().isAfter(end)) {
				throw new IllegalArgumentException("All shift dates must be within the planning window (from " + start + " to " + end + "). Found date outside range: " + dto.date() + ". You may only submit plans for dates starting " + delayDays + " days from today.");
			}
		}
		List<ShiftPlan> existing = planRepository.findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc(userId, start, end);
		ShiftPlan plan = existing.stream().filter(p -> p.getStatus() == ShiftPlanStatus.PENDING_APPROVAL).findFirst()
				.or(() -> existing.stream().filter(p -> p.getStatus() == ShiftPlanStatus.DRAFT).findFirst())
				.or(() -> existing.stream().filter(p -> p.getStatus() == ShiftPlanStatus.REJECTED).findFirst())
				.orElseGet(() -> {
					ShiftPlan p = new ShiftPlan();
					p.setUserId(userId);
					p.setPeriodStart(start);
					p.setPeriodEnd(end);
					p.setStatus(ShiftPlanStatus.DRAFT);
					return planRepository.save(p);
				});
		// Approved plans can be resubmitted after user edits (sends plan back to PENDING_APPROVAL).
		plan.setPeriodStart(start);
		plan.setPeriodEnd(end);
		log.debug("Submit shift plan: using plan id={}, period {} to {}", plan.getId(), plan.getPeriodStart(), plan.getPeriodEnd());

		List<ShiftEntry> existingEntries = entryRepository.findByPlanIdOrderByEntryDateAscStartTimeAsc(plan.getId());
		List<ShiftEntry> approvedEntries = existingEntries.stream()
				.filter(e -> e.getReviewStatus() == ShiftEntryReviewStatus.APPROVED)
				.toList();

		// Resubmission behavior:
		// - If plan is in review (PENDING_APPROVAL): only replace entries that are REJECTED / CHANGES_REQUESTED.
		//   Keep APPROVED and still-PENDING entries untouched.
		// - If plan is DRAFT/REJECTED: treat as full submit (replace all existing entries).
		List<ShiftEntry> toDelete;
		if (plan.getStatus() == ShiftPlanStatus.PENDING_APPROVAL) {
			toDelete = existingEntries.stream()
					.filter(e -> e.getReviewStatus() == ShiftEntryReviewStatus.REJECTED
							|| e.getReviewStatus() == ShiftEntryReviewStatus.CHANGES_REQUESTED)
					.toList();
		} else {
			toDelete = existingEntries;
		}
		if (!toDelete.isEmpty()) entryRepository.deleteAll(toDelete);

		for (ShiftEntryDto dto : entries) {
			if (dto.date().isBefore(start) || dto.date().isAfter(end) || isHoliday(dto.date()) || dto.date().getDayOfWeek() == DayOfWeek.SUNDAY) continue;
			// Skip creating duplicates of already-approved entries (keeps them locked and green).
			boolean alreadyApproved = approvedEntries.stream().anyMatch(a ->
					Objects.equals(a.getEntryDate(), dto.date())
							&& Objects.equals(a.getStartTime(), LocalTime.parse(dto.startTime()))
							&& Objects.equals(a.getEndTime(), LocalTime.parse(dto.endTime()))
			);
			if (alreadyApproved) continue;
			ShiftEntry e = new ShiftEntry();
			e.setPlanId(plan.getId());
			e.setEntryDate(dto.date());
			e.setStartTime(LocalTime.parse(dto.startTime()));
			e.setEndTime(LocalTime.parse(dto.endTime()));
			e.setShiftType(dto.shiftType());
			e.setMinutes(dto.minutes());
			e.setReviewStatus(ShiftEntryReviewStatus.PENDING_APPROVAL);
			e.setReviewComment(null);
			e.setReviewedAt(null);
			e.setReviewedById(null);
			entryRepository.save(e);
		}
		// No strict weekly-hours validation: allow partial/empty weeks; admin can reject if needed
		plan.setStatus(ShiftPlanStatus.PENDING_APPROVAL);
		plan.setSubmittedAt(java.time.Instant.now());
		plan.setRejectionReason(null);
		planRepository.save(plan);
	}

	public boolean canSubmit(List<ShiftEntryDto> entries, Employee employee) {
		if (entries == null || entries.isEmpty()) return false;
		ContractInfoDto info = buildContractInfo(employee, entries);
		return info.weeklySummaries().stream().allMatch(WeekSummaryDto::valid);
	}

	/** Returns a message for the dashboard when the user has an approved plan (e.g. "Your weekly shift plan for KW 14 2026 has been approved."). */
	public java.util.Optional<String> getLatestApprovedPlanMessage(Long userId) {
		return planRepository.findFirstByDeletedAtIsNullAndUserIdAndStatusOrderByUpdatedAtDesc(userId, ShiftPlanStatus.APPROVED)
				.map(plan -> {
					LocalDate start = plan.getPeriodStart();
					int week = start.get(WeekFields.of(java.util.Locale.GERMANY).weekOfWeekBasedYear());
					int year = start.getYear();
					return "Your weekly shift plan for KW " + week + " " + year + " has been approved.";
				});
	}

	/** Approved shift summary lines for dashboard: one string per entry, e.g. "Mon 15 Apr 08:00–16:00 (8h)". */
	public List<String> getApprovedShiftSummaryLines(Long userId, YearMonth month) {
		List<ShiftEntry> entries = getApprovedShiftEntries(userId, month);
		DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.GERMANY);
		DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
		List<String> lines = new ArrayList<>();
		for (ShiftEntry e : entries) {
			String dayStr = e.getEntryDate().format(dayFmt);
			String timeStr = e.getStartTime().format(timeFmt) + "–" + e.getEndTime().format(timeFmt);
			int hours = e.getMinutes() / 60;
			int mins = e.getMinutes() % 60;
			String hoursStr = mins > 0 ? hours + "h " + mins + "m" : hours + "h";
			lines.add(dayStr + " " + timeStr + " (" + hoursStr + ")");
		}
		return lines;
	}

	/**
	 * Approved shift entries for a given month (deduplicated).
	 * Duplicates can occur when multiple approved plans overlap a month (e.g., resubmissions).
	 */
	public List<ShiftEntry> getApprovedShiftEntries(Long userId, YearMonth month) {
		LocalDate first = month.atDay(1);
		LocalDate last = month.atEndOfMonth();
		List<ShiftPlan> plans = planRepository.findByUserIdAndStatusAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByPeriodStartAsc(
				userId, ShiftPlanStatus.APPROVED, last, first);
		List<ShiftEntry> raw = new ArrayList<>();
		for (ShiftPlan plan : plans) {
			raw.addAll(entryRepository.findByPlanIdAndEntryDateBetweenOrderByEntryDateAscStartTimeAsc(plan.getId(), first, last));
		}
		// Deduplicate by date+start+end (and minutes) to avoid repeated lines in exports.
		Set<String> seen = new HashSet<>();
		List<ShiftEntry> dedup = new ArrayList<>();
		for (ShiftEntry e : raw) {
			if (e == null || e.getEntryDate() == null || e.getStartTime() == null || e.getEndTime() == null) continue;
			String key = e.getEntryDate() + "|" + e.getStartTime() + "|" + e.getEndTime() + "|" + e.getMinutes();
			if (seen.add(key)) dedup.add(e);
		}
		dedup.sort(Comparator.comparing(ShiftEntry::getEntryDate).thenComparing(ShiftEntry::getStartTime));
		return dedup;
	}

	/** Current shift plan status in the allowed window (DRAFT, PENDING_APPROVAL, APPROVED, REJECTED) or null if none. */
	public String getCurrentPlanStatus(Long userId) {
		LocalDate start = getAllowedWindowStart();
		LocalDate end = getAllowedWindowEnd();
		List<ShiftPlan> plans = planRepository.findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc(userId, start, end);
		return plans.stream()
				.map(p -> p.getStatus().name())
				.reduce((a, b) -> {
					if ("DRAFT".equals(a) || "DRAFT".equals(b)) return "DRAFT";
					if ("PENDING_APPROVAL".equals(a) || "PENDING_APPROVAL".equals(b)) return "PENDING_APPROVAL";
					if ("REJECTED".equals(a) || "REJECTED".equals(b)) return "REJECTED";
					return "APPROVED";
				})
				.orElse(null);
	}

	/** Planned hours in the current week (Mon–Sun) from plans in the current window. */
	public double getPlannedHoursThisWeek(Long userId) {
		LocalDate now = LocalDate.now();
		WeekFields wf = WeekFields.of(java.util.Locale.GERMANY);
		LocalDate weekStart = now.with(wf.dayOfWeek(), 1);
		LocalDate weekEnd = weekStart.plusDays(6);
		LocalDate windowStart = getAllowedWindowStart();
		LocalDate windowEnd = getAllowedWindowEnd();
		if (weekEnd.isBefore(windowStart) || weekStart.isAfter(windowEnd)) return 0;
		LocalDate start = weekStart.isBefore(windowStart) ? windowStart : weekStart;
		LocalDate end = weekEnd.isAfter(windowEnd) ? windowEnd : weekEnd;
		List<ShiftPlan> plans = planRepository.findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc(userId, windowStart, windowEnd);
		int totalMinutes = 0;
		for (ShiftPlan plan : plans) {
			for (ShiftEntry e : entryRepository.findByPlanIdAndEntryDateBetweenOrderByEntryDateAscStartTimeAsc(plan.getId(), start, end)) {
				totalMinutes += e.getMinutes();
			}
		}
		return totalMinutes / 60.0;
	}
}
