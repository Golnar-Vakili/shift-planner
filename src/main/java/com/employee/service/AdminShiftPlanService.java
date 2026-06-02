package com.employee.service;

import com.employee.config.ShiftPresetConfig;
import com.employee.domain.ContractType;
import com.employee.domain.Employee;
import com.employee.domain.ShiftEntry;
import com.employee.domain.ShiftEntryReviewStatus;
import com.employee.domain.ShiftType;
import com.employee.domain.ShiftPlan;
import com.employee.domain.ShiftPlanStatus;
import com.employee.domain.NotificationType;
import com.employee.dto.AdminShiftEntryReviewUpdateDto;
import com.employee.dto.AdminShiftPlanDetailDto;
import com.employee.dto.AdminShiftPlanEntryDto;
import com.employee.dto.AdminShiftPlanListItemDto;
import com.employee.repository.EmployeeRepository;
import com.employee.repository.ShiftEntryRepository;
import com.employee.repository.ShiftPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminShiftPlanService {

	private final ShiftPlanRepository shiftPlanRepository;
	private final ShiftEntryRepository shiftEntryRepository;
	private final EmployeeRepository employeeRepository;
	private final ShiftPresetConfig presetConfig;
	private final NotificationService notificationService;

	public AdminShiftPlanService(ShiftPlanRepository shiftPlanRepository,
	                             ShiftEntryRepository shiftEntryRepository,
	                             EmployeeRepository employeeRepository,
	                             ShiftPresetConfig presetConfig,
	                             NotificationService notificationService) {
		this.shiftPlanRepository = shiftPlanRepository;
		this.shiftEntryRepository = shiftEntryRepository;
		this.employeeRepository = employeeRepository;
		this.presetConfig = presetConfig;
		this.notificationService = notificationService;
	}

	/**
	 * List shift plans for admin, optionally filtered by status. PENDING_APPROVAL first when no filter.
	 */
	public List<AdminShiftPlanListItemDto> listForAdmin(ShiftPlanStatus statusFilter) {
		List<ShiftPlan> plans = statusFilter != null
				? shiftPlanRepository.findAllByStatusOrderByUpdatedAtDesc(statusFilter)
				: shiftPlanRepository.findAllByOrderByUpdatedAtDesc();
		if (plans.isEmpty()) return List.of();

		List<Long> employeeIds = plans.stream().map(ShiftPlan::getUserId).distinct().toList();
		Map<Long, Employee> employees = employeeRepository.findAllById(employeeIds).stream()
				.collect(Collectors.toMap(Employee::getId, e -> e));

		return plans.stream()
				.map(plan -> toListItem(plan, employees.get(plan.getUserId())))
				.filter(item -> !("DRAFT".equals(item.status()) && item.totalPlannedHours() <= 0))
				.sorted(Comparator
						.comparingInt((AdminShiftPlanListItemDto x) -> statusOrder(x.status()))
						.thenComparing(AdminShiftPlanListItemDto::submittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
	}

	/** List all shift plans for admin (no status filter). PENDING_APPROVAL first, then by submittedAt desc. */
	public List<AdminShiftPlanListItemDto> listForAdmin() {
		return listForAdmin(null);
	}

	@Transactional
	public void softDeletePlans(Collection<Long> planIds, long adminId) {
		if (planIds == null || planIds.isEmpty()) return;
		Instant now = Instant.now();
		List<ShiftPlan> plans = shiftPlanRepository.findAllById(planIds);
		for (ShiftPlan p : plans) {
			if (p == null) continue;
			if (p.getDeletedAt() != null) continue;
			p.setDeletedAt(now);
			p.setDeletedById(adminId);
		}
		shiftPlanRepository.saveAll(plans);
	}

	@Transactional
	public void softDeletePlan(long planId, long adminId) {
		softDeletePlans(List.of(planId), adminId);
	}

	private int statusOrder(String status) {
		return switch (status != null ? status : "") {
			case "PENDING_APPROVAL" -> 0;
			case "APPROVED" -> 1;
			case "REJECTED" -> 2;
			default -> 3; // DRAFT
		};
	}

	private AdminShiftPlanListItemDto toListItem(ShiftPlan plan, Employee employee) {
		String name = employee != null ? (employee.getDisplayName() != null && !employee.getDisplayName().isBlank()
				? employee.getDisplayName() : employee.getUsername()) : "—";
		String periodStart = plan.getPeriodStart() != null ? plan.getPeriodStart().toString() : "—";
		String periodEnd = plan.getPeriodEnd() != null ? plan.getPeriodEnd().toString() : "—";

		List<ShiftEntry> entries = shiftEntryRepository.findByPlanIdAndEntryDateBetweenOrderByEntryDateAscStartTimeAsc(
				plan.getId(), plan.getPeriodStart(), plan.getPeriodEnd());
		int totalMinutes = entries.stream().mapToInt(ShiftEntry::getMinutes).sum();
		double totalHours = Math.round(totalMinutes / 6.0) / 10.0;

		String submittedFormatted = plan.getSubmittedAt() != null
				? DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(plan.getSubmittedAt())
				: "—";

		return new AdminShiftPlanListItemDto(
				plan.getId(),
				plan.getUserId(),
				name,
				periodStart,
				periodEnd,
				totalHours,
				plan.getStatus().name(),
				plan.getSubmittedAt(),
				submittedFormatted
		);
	}

	/**
	 * Get full detail of a shift plan for admin.
	 */
	public AdminShiftPlanDetailDto getDetailForAdmin(long planId) {
		ShiftPlan plan = shiftPlanRepository.findById(planId)
				.orElseThrow(() -> new IllegalArgumentException("Shift plan not found: " + planId));
		Employee employee = employeeRepository.findById(plan.getUserId()).orElse(null);
		String employeeName = employee != null ? (employee.getDisplayName() != null && !employee.getDisplayName().isBlank()
				? employee.getDisplayName() : employee.getUsername()) : "—";
		String contractLabel = employee != null ? contractTypeLabel(employee.getContractType()) : "—";
		java.math.BigDecimal expectedWeekly = employee != null && employee.getAllowedHoursPerWeek() != null
				? employee.getAllowedHoursPerWeek() : java.math.BigDecimal.valueOf(40);

		List<ShiftEntry> entries = shiftEntryRepository.findByPlanIdAndEntryDateBetweenOrderByEntryDateAscStartTimeAsc(
				plan.getId(), plan.getPeriodStart(), plan.getPeriodEnd());
		int totalMinutes = entries.stream().mapToInt(ShiftEntry::getMinutes).sum();
		double totalHours = Math.round(totalMinutes / 6.0) / 10.0;

		// Only include entries with non-null date so the template never sees null dates
		List<AdminShiftPlanEntryDto> entryDtos = entries.stream()
				.filter(e -> e.getEntryDate() != null)
				.map(e -> {
					String typeLabel = e.getShiftType() != null ? e.getShiftType().name() : "—";
					String start = e.getStartTime() != null ? e.getStartTime().toString() : "—";
					String end = e.getEndTime() != null ? e.getEndTime().toString() : "—";
					String rs = e.getReviewStatus() != null ? e.getReviewStatus().name() : null;
					String comment = e.getReviewComment();
					return new AdminShiftPlanEntryDto(
							e.getId(),
							e.getEntryDate(),
							start,
							end,
							typeLabel,
							e.getMinutes(),
							rs,
							comment
					);
				})
				.toList();

		String submittedFormatted = plan.getSubmittedAt() != null
				? DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(plan.getSubmittedAt())
				: "—";
		String reviewedFormatted = plan.getReviewedAt() != null
				? DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(plan.getReviewedAt())
				: "—";

		return new AdminShiftPlanDetailDto(
				plan.getId(),
				plan.getUserId(),
				employeeName,
				contractLabel,
				expectedWeekly,
				totalHours,
				entryDtos,
				plan.getStatus().name(),
				plan.getSubmittedAt(),
				plan.getReviewedAt(),
				plan.getReviewedById(),
				plan.getRejectionReason(),
				submittedFormatted,
				reviewedFormatted
		);
	}

	private static String contractTypeLabel(ContractType ct) {
		if (ct == null) return "—";
		return switch (ct) {
			case FULL_TIME -> "Full-time";
			case PART_TIME -> "Part-time";
			case MINI_JOB -> "Mini-job";
		};
	}

	@Transactional
	public void reviewEntries(long planId, long reviewerId, List<AdminShiftEntryReviewUpdateDto> updates) {
		ShiftPlan plan = shiftPlanRepository.findById(planId)
				.orElseThrow(() -> new IllegalArgumentException("Shift plan not found: " + planId));
		// Admin may review again even after approval (e.g. request changes again).
		if (plan.getStatus() != ShiftPlanStatus.PENDING_APPROVAL && plan.getStatus() != ShiftPlanStatus.APPROVED) {
			throw new IllegalArgumentException("Only PENDING_APPROVAL or APPROVED plans can be reviewed. Current status: " + plan.getStatus());
		}
		if (updates == null || updates.isEmpty()) return;

		List<ShiftEntry> entries = shiftEntryRepository.findByPlanIdOrderByEntryDateAscStartTimeAsc(planId);
		Map<Long, ShiftEntry> byId = entries.stream()
				.filter(e -> e.getId() != null)
				.collect(Collectors.toMap(ShiftEntry::getId, e -> e, (a, b) -> a));

		Instant now = Instant.now();
		for (AdminShiftEntryReviewUpdateDto u : updates) {
			if (u == null || u.entryId() == null) continue;
			ShiftEntry entry = byId.get(u.entryId());
			if (entry == null) continue;
			String raw = (u.status() != null ? u.status().trim().toUpperCase() : "");
			if (raw.isEmpty()) continue;
			ShiftEntryReviewStatus rs;
			try {
				rs = ShiftEntryReviewStatus.valueOf(raw);
			} catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("Invalid entry status: " + u.status());
			}
			entry.setReviewStatus(rs);
			String comment = u.comment();
			if (comment != null && comment.length() > 500) comment = comment.substring(0, 500);
			entry.setReviewComment(comment);
			entry.setReviewedAt(now);
			entry.setReviewedById(reviewerId);
		}
		shiftEntryRepository.saveAll(entries);

		// Keep row-level actions row-level:
		// - If plan is already APPROVED, do NOT flip the entire plan back to PENDING_APPROVAL here.
		//   (Otherwise the admin UI switches to "review all rows" mode, which is not desired for single-row actions.)
		// - If plan is currently PENDING_APPROVAL, keep existing behavior: approve whole plan only when all entries approved-like.
		if (plan.getStatus() == ShiftPlanStatus.PENDING_APPROVAL) {
			boolean allApprovedLike = !entries.isEmpty() && entries.stream().allMatch(e ->
					e.getReviewStatus() == ShiftEntryReviewStatus.APPROVED
							|| e.getReviewStatus() == ShiftEntryReviewStatus.ADMIN_UPDATED
							|| e.getReviewStatus() == ShiftEntryReviewStatus.ADMIN_CANCELLED
			);
			if (allApprovedLike) {
				plan.setStatus(ShiftPlanStatus.APPROVED);
			}
		}
		plan.setReviewedAt(now);
		plan.setReviewedById(reviewerId);
		plan.setRejectionReason(null);
		shiftPlanRepository.save(plan);
	}

	@Transactional
	public void requestChangeEntry(long planId, long entryId, long reviewerId, String comment) {
		ShiftPlan plan = shiftPlanRepository.findById(planId)
				.orElseThrow(() -> new IllegalArgumentException("Shift plan not found: " + planId));
		ShiftEntry entry = shiftEntryRepository.findById(entryId)
				.orElseThrow(() -> new IllegalArgumentException("Shift entry not found: " + entryId));
		if (!entry.getPlanId().equals(plan.getId())) {
			throw new IllegalArgumentException("Shift entry does not belong to this plan.");
		}
		entry.setReviewStatus(ShiftEntryReviewStatus.CHANGES_REQUESTED);
		String c = comment != null ? comment.trim() : null;
		if (c != null && c.length() > 500) c = c.substring(0, 500);
		entry.setReviewComment(c);
		entry.setReviewedAt(Instant.now());
		entry.setReviewedById(reviewerId);
		shiftEntryRepository.save(entry);

		// Do NOT flip the whole plan back to PENDING_APPROVAL here.
		// This is a row-level action: only the selected entry becomes CHANGES_REQUESTED.
		plan.setReviewedAt(Instant.now());
		plan.setReviewedById(reviewerId);
		shiftPlanRepository.save(plan);
	}

	@Transactional
	public void cancelEntry(long planId, long entryId, long reviewerId, String comment) {
		ShiftPlan plan = shiftPlanRepository.findById(planId)
				.orElseThrow(() -> new IllegalArgumentException("Shift plan not found: " + planId));
		ShiftEntry entry = shiftEntryRepository.findById(entryId)
				.orElseThrow(() -> new IllegalArgumentException("Shift entry not found: " + entryId));
		if (!entry.getPlanId().equals(plan.getId())) {
			throw new IllegalArgumentException("Shift entry does not belong to this plan.");
		}
		entry.setReviewStatus(ShiftEntryReviewStatus.ADMIN_CANCELLED);
		String c = comment != null ? comment.trim() : null;
		if (c != null && c.length() > 500) c = c.substring(0, 500);
		entry.setReviewComment(c);
		entry.setReviewedAt(Instant.now());
		entry.setReviewedById(reviewerId);
		shiftEntryRepository.save(entry);

		plan.setReviewedAt(Instant.now());
		plan.setReviewedById(reviewerId);
		shiftPlanRepository.save(plan);
	}

	@Transactional
	public void adminUpdateEntry(long planId,
	                             long entryId,
	                             long reviewerId,
	                             ShiftType shiftType,
	                             String startTime,
	                             String endTime,
	                             String comment) {
		ShiftPlan plan = shiftPlanRepository.findById(planId)
				.orElseThrow(() -> new IllegalArgumentException("Shift plan not found: " + planId));
		ShiftEntry entry = shiftEntryRepository.findById(entryId)
				.orElseThrow(() -> new IllegalArgumentException("Shift entry not found: " + entryId));
		if (!entry.getPlanId().equals(plan.getId())) {
			throw new IllegalArgumentException("Shift entry does not belong to this plan.");
		}

		ShiftType type = (shiftType != null ? shiftType : entry.getShiftType());
		LocalTime start;
		LocalTime end;
		int minutes;
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
				start = (startTime != null && !startTime.isBlank()) ? LocalTime.parse(startTime.trim()) : entry.getStartTime();
				end = (endTime != null && !endTime.isBlank()) ? LocalTime.parse(endTime.trim()) : entry.getEndTime();
				if (start == null) start = LocalTime.of(8, 0);
				if (end == null) end = LocalTime.of(16, 0);
				if (!end.isAfter(start)) end = end.plusHours(24);
				minutes = (int) java.time.Duration.between(start, end).toMinutes();
				if (minutes < 120 || minutes > 720) throw new IllegalArgumentException("Custom shift must be between 2 and 12 hours.");
			}
			default -> throw new IllegalArgumentException("Unknown shift type.");
		}

		entry.setShiftType(type);
		entry.setStartTime(start);
		entry.setEndTime(end);
		entry.setMinutes(minutes);
		entry.setReviewStatus(ShiftEntryReviewStatus.ADMIN_UPDATED);
		String c = comment != null ? comment.trim() : null;
		if (c != null && c.length() > 500) c = c.substring(0, 500);
		entry.setReviewComment(c);
		entry.setReviewedAt(Instant.now());
		entry.setReviewedById(reviewerId);
		shiftEntryRepository.save(entry);

		plan.setReviewedAt(Instant.now());
		plan.setReviewedById(reviewerId);
		shiftPlanRepository.save(plan);
	}

	@Transactional
	public void approve(long planId, long reviewerId) {
		ShiftPlan plan = shiftPlanRepository.findById(planId)
				.orElseThrow(() -> new IllegalArgumentException("Shift plan not found: " + planId));
		if (plan.getStatus() != ShiftPlanStatus.PENDING_APPROVAL) {
			throw new IllegalArgumentException("Only PENDING_APPROVAL plans can be approved. Current status: " + plan.getStatus());
		}
		Instant now = Instant.now();
		// Plan-level approve should also mark all entries approved so the user calendar turns green and locks.
		List<ShiftEntry> entries = shiftEntryRepository.findByPlanIdOrderByEntryDateAscStartTimeAsc(planId);
		for (ShiftEntry e : entries) {
			e.setReviewStatus(ShiftEntryReviewStatus.APPROVED);
			e.setReviewedAt(now);
			e.setReviewedById(reviewerId);
			// keep comment if any (optional), but typically clear
		}
		shiftEntryRepository.saveAll(entries);

		plan.setStatus(ShiftPlanStatus.APPROVED);
		plan.setReviewedAt(now);
		plan.setReviewedById(reviewerId);
		plan.setRejectionReason(null);
		shiftPlanRepository.save(plan);

		notificationService.notifyUser(
				plan.getUserId(),
				NotificationType.SHIFT_UPDATE,
				"Your shift plan was approved.",
				"/dashboard/shifts"
		);
	}

	@Transactional
	public void reject(long planId, long reviewerId, String reason) {
		ShiftPlan plan = shiftPlanRepository.findById(planId)
				.orElseThrow(() -> new IllegalArgumentException("Shift plan not found: " + planId));
		if (plan.getStatus() != ShiftPlanStatus.PENDING_APPROVAL) {
			throw new IllegalArgumentException("Only PENDING_APPROVAL plans can be rejected. Current status: " + plan.getStatus());
		}
		Instant now = Instant.now();
		// Plan-level reject marks all entries rejected, so they show red and become editable for resubmission.
		List<ShiftEntry> entries = shiftEntryRepository.findByPlanIdOrderByEntryDateAscStartTimeAsc(planId);
		for (ShiftEntry e : entries) {
			e.setReviewStatus(ShiftEntryReviewStatus.REJECTED);
			e.setReviewedAt(now);
			e.setReviewedById(reviewerId);
		}
		shiftEntryRepository.saveAll(entries);

		plan.setStatus(ShiftPlanStatus.REJECTED);
		plan.setReviewedAt(now);
		plan.setReviewedById(reviewerId);
		plan.setRejectionReason(reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason);
		shiftPlanRepository.save(plan);

		notificationService.notifyUser(
				plan.getUserId(),
				NotificationType.SHIFT_UPDATE,
				"Your shift plan was rejected.",
				"/dashboard/shifts"
		);
	}
}
