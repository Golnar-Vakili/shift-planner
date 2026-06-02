package com.employee.service;

import com.employee.domain.ContractType;
import com.employee.domain.Employee;
import com.employee.domain.VacationDayReviewStatus;
import com.employee.domain.VacationRequest;
import com.employee.domain.VacationRequestDay;
import com.employee.domain.VacationRequestStatus;
import com.employee.domain.NotificationType;
import com.employee.dto.AdminVacationDayReviewRowDto;
import com.employee.dto.AdminVacationRequestDetailDto;
import com.employee.dto.AdminVacationRequestListItemDto;
import com.employee.repository.EmployeeRepository;
import com.employee.repository.VacationRequestDayRepository;
import com.employee.repository.VacationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminVacationRequestService {

	private final VacationRequestRepository vacationRequestRepository;
	private final VacationRequestDayRepository vacationRequestDayRepository;
	private final EmployeeRepository employeeRepository;
	private final NotificationService notificationService;

	public AdminVacationRequestService(VacationRequestRepository vacationRequestRepository,
	                                   VacationRequestDayRepository vacationRequestDayRepository,
	                                   EmployeeRepository employeeRepository,
	                                   NotificationService notificationService) {
		this.vacationRequestRepository = vacationRequestRepository;
		this.vacationRequestDayRepository = vacationRequestDayRepository;
		this.employeeRepository = employeeRepository;
		this.notificationService = notificationService;
	}

	/**
	 * List all vacation requests for admin. PENDING first, then by submittedAt desc.
	 */
	public List<AdminVacationRequestListItemDto> listForAdmin() {
		List<VacationRequest> requests = vacationRequestRepository.findAllByOrderByUpdatedAtDesc();
		if (requests.isEmpty()) return List.of();

		List<Long> employeeIds = requests.stream().map(VacationRequest::getEmployeeId).distinct().toList();
		Map<Long, Employee> employees = employeeRepository.findAllById(employeeIds).stream()
				.collect(Collectors.toMap(Employee::getId, e -> e));

		return requests.stream()
				.map(req -> toListItem(req, employees.get(req.getEmployeeId())))
				.sorted(Comparator
						.comparingInt((AdminVacationRequestListItemDto x) -> statusOrder(x.status()))
						.thenComparing(AdminVacationRequestListItemDto::submittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
	}

	@Transactional
	public void softDeleteRequests(Collection<Long> requestIds, long adminId) {
		if (requestIds == null || requestIds.isEmpty()) return;
		Instant now = Instant.now();
		List<VacationRequest> requests = vacationRequestRepository.findAllById(requestIds);
		for (VacationRequest r : requests) {
			if (r == null) continue;
			if (r.getDeletedAt() != null) continue;
			r.setDeletedAt(now);
			r.setDeletedById(adminId);
		}
		vacationRequestRepository.saveAll(requests);
	}

	@Transactional
	public void softDeleteRequest(long requestId, long adminId) {
		softDeleteRequests(List.of(requestId), adminId);
	}

	private int statusOrder(String status) {
		return switch (status != null ? status : "") {
			case "PENDING" -> 0;
			case "APPROVED" -> 1;
			case "REJECTED" -> 2;
			default -> 3;
		};
	}

	private AdminVacationRequestListItemDto toListItem(VacationRequest req, Employee employee) {
		String name = employee != null ? (employee.getDisplayName() != null && !employee.getDisplayName().isBlank()
				? employee.getDisplayName() : employee.getUsername()) : "—";
		int count = vacationRequestDayRepository.findByVacationRequestIdOrderByRequestDateAsc(req.getId()).size();
		String submittedFormatted = req.getSubmittedAt() != null
				? DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(req.getSubmittedAt())
				: "—";
		return new AdminVacationRequestListItemDto(
				req.getId(),
				req.getEmployeeId(),
				name,
				req.getYear(),
				count,
				req.getStatus().name(),
				req.getSubmittedAt(),
				submittedFormatted
		);
	}

	/**
	 * Get full detail of a vacation request for admin.
	 */
	public AdminVacationRequestDetailDto getDetailForAdmin(long requestId) {
		VacationRequest req = vacationRequestRepository.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("Vacation request not found: " + requestId));
		Employee employee = employeeRepository.findById(req.getEmployeeId()).orElse(null);
		String employeeName = employee != null ? (employee.getDisplayName() != null && !employee.getDisplayName().isBlank()
				? employee.getDisplayName() : employee.getUsername()) : "—";
		String contractLabel = employee != null ? contractTypeLabel(employee.getContractType()) : "—";
		int entitlement = employee != null ? (employee.getAllowedVacationDays() != null ? employee.getAllowedVacationDays() : 20) : 0;
		List<VacationRequestDay> dayEntities = vacationRequestDayRepository.findByVacationRequestIdOrderByRequestDateAsc(req.getId());
		List<AdminVacationDayReviewRowDto> dayRows = dayEntities.stream()
				.map(this::toDayReviewRow)
				.toList();

		String submittedFormatted = req.getSubmittedAt() != null
				? DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(req.getSubmittedAt())
				: "—";
		String reviewedFormatted = req.getReviewedAt() != null
				? DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(req.getReviewedAt())
				: "—";

		return new AdminVacationRequestDetailDto(
				req.getId(),
				req.getEmployeeId(),
				employeeName,
				req.getYear(),
				contractLabel,
				entitlement,
				dayRows,
				req.getStatus().name(),
				req.getSubmittedAt(),
				req.getReviewedAt(),
				req.getReviewedById(),
				req.getRejectionReason(),
				req.getReviewComment(),
				submittedFormatted,
				reviewedFormatted
		);
	}

	private AdminVacationDayReviewRowDto toDayReviewRow(VacationRequestDay d) {
		VacationDayReviewStatus rs = d.getReviewStatus();
		String code = rs != null ? rs.name() : null;
		String reviewedFmt = d.getReviewedAt() != null
				? DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(d.getReviewedAt())
				: null;
		return new AdminVacationDayReviewRowDto(
				d.getId(),
				d.getRequestDate(),
				code,
				dayReviewStatusLabel(rs),
				d.getReviewComment(),
				reviewedFmt
		);
	}

	private static String dayReviewStatusLabel(VacationDayReviewStatus rs) {
		if (rs == null) return "Unreviewed";
		return switch (rs) {
			case PENDING_APPROVAL -> "Pending";
			case APPROVED -> "Approved";
			case REJECTED -> "Rejected";
			case CHANGES_REQUESTED -> "Change requested";
		};
	}

	/**
	 * Admin-only per-day decision.
	 */
	@Transactional
	public void saveDayReview(long requestId, long dayId, String decision, String comment, long adminId) {
		VacationRequest req = vacationRequestRepository.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("Vacation request not found: " + requestId));
		if (req.getDeletedAt() != null) {
			throw new IllegalArgumentException("This vacation request is no longer available.");
		}
		VacationRequestDay day = vacationRequestDayRepository.findById(dayId)
				.orElseThrow(() -> new IllegalArgumentException("Vacation day not found: " + dayId));
		if (!day.getVacationRequestId().equals(requestId)) {
			throw new IllegalArgumentException("That day does not belong to this vacation request.");
		}
		VacationDayReviewStatus status = parseDayDecision(decision);
		String c = comment != null ? comment.trim() : null;
		if (c != null && c.length() > 500) c = c.substring(0, 500);
		if (c != null && c.isEmpty()) c = null;
		day.setReviewStatus(status);
		day.setReviewComment(c);
		day.setReviewedAt(Instant.now());
		day.setReviewedById(adminId);
		vacationRequestDayRepository.save(day);

		// If at least one day requires employee action, unlock request editing flow.
		if (requiresEmployeeAction(status) && canPromoteRequestToChangesRequested(req.getStatus())) {
			req.setStatus(VacationRequestStatus.CHANGES_REQUESTED);
			vacationRequestRepository.save(req);
		}
	}

	private static boolean requiresEmployeeAction(VacationDayReviewStatus status) {
		return status == VacationDayReviewStatus.REJECTED
				|| status == VacationDayReviewStatus.CHANGES_REQUESTED;
	}

	private static boolean canPromoteRequestToChangesRequested(VacationRequestStatus status) {
		return status == VacationRequestStatus.PENDING
				|| status == VacationRequestStatus.APPROVED;
	}

	/**
	 * Day row after a save (for AJAX refresh without full page reload).
	 */
	public AdminVacationDayReviewRowDto getDayReviewRowForRequest(long requestId, long dayId) {
		VacationRequestDay day = vacationRequestDayRepository.findById(dayId)
				.orElseThrow(() -> new IllegalArgumentException("Vacation day not found: " + dayId));
		if (!day.getVacationRequestId().equals(requestId)) {
			throw new IllegalArgumentException("That day does not belong to this vacation request.");
		}
		return toDayReviewRow(day);
	}

	public String getRequestStatusName(long requestId) {
		return vacationRequestRepository.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("Vacation request not found: " + requestId))
				.getStatus()
				.name();
	}

	private static VacationDayReviewStatus parseDayDecision(String decision) {
		if (decision == null || decision.isBlank()) {
			throw new IllegalArgumentException("Choose approve, reject, or request change for this day.");
		}
		return switch (decision.trim().toUpperCase()) {
			case "APPROVE" -> VacationDayReviewStatus.APPROVED;
			case "REJECT" -> VacationDayReviewStatus.REJECTED;
			case "REQUEST_CHANGE" -> VacationDayReviewStatus.CHANGES_REQUESTED;
			default -> throw new IllegalArgumentException("Invalid day decision: " + decision);
		};
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
	public void approve(long requestId, long reviewerId) {
		VacationRequest req = vacationRequestRepository.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("Vacation request not found: " + requestId));
		if (req.getStatus() != VacationRequestStatus.PENDING) {
			throw new IllegalArgumentException("Only PENDING requests can be approved. Current status: " + req.getStatus());
		}
		req.setStatus(VacationRequestStatus.APPROVED);
		req.setReviewedAt(Instant.now());
		req.setReviewedById(reviewerId);
		req.setRejectionReason(null);
		req.setReviewComment(null);
		vacationRequestRepository.save(req);

		notificationService.notifyUser(
				req.getEmployeeId(),
				NotificationType.VACATION_UPDATE,
				"Your vacation request for " + req.getYear() + " was approved.",
				"/dashboard/holidays"
		);
	}

	@Transactional
	public void reject(long requestId, long reviewerId, String reason) {
		VacationRequest req = vacationRequestRepository.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("Vacation request not found: " + requestId));
		if (req.getStatus() != VacationRequestStatus.PENDING) {
			throw new IllegalArgumentException("Only PENDING requests can be rejected. Current status: " + req.getStatus());
		}
		req.setStatus(VacationRequestStatus.REJECTED);
		req.setReviewedAt(Instant.now());
		req.setReviewedById(reviewerId);
		req.setRejectionReason(reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason);
		req.setReviewComment(null);
		vacationRequestRepository.save(req);

		notificationService.notifyUser(
				req.getEmployeeId(),
				NotificationType.VACATION_UPDATE,
				"Your vacation request for " + req.getYear() + " was rejected.",
				"/dashboard/holidays"
		);
	}

	@Transactional
	public void requestChanges(long requestId, long reviewerId, String comment) {
		VacationRequest req = vacationRequestRepository.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("Vacation request not found: " + requestId));
		if (!(req.getStatus() == VacationRequestStatus.PENDING || req.getStatus() == VacationRequestStatus.APPROVED)) {
			throw new IllegalArgumentException("Changes can only be requested for PENDING or APPROVED requests. Current status: " + req.getStatus());
		}
		String c = comment != null ? comment.trim() : null;
		if (c != null && c.length() > 500) c = c.substring(0, 500);
		req.setStatus(VacationRequestStatus.CHANGES_REQUESTED);
		req.setReviewComment(c);
		req.setReviewedAt(Instant.now());
		req.setReviewedById(reviewerId);
		req.setRejectionReason(null);
		vacationRequestRepository.save(req);

		notificationService.notifyUser(
				req.getEmployeeId(),
				NotificationType.VACATION_UPDATE,
				"Your vacation request requires changes.",
				"/dashboard/holidays"
		);
	}

	@Transactional
	public void adminCancel(long requestId, long reviewerId, String comment) {
		VacationRequest req = vacationRequestRepository.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("Vacation request not found: " + requestId));
		if (req.getStatus() == VacationRequestStatus.DRAFT) {
			throw new IllegalArgumentException("Draft requests cannot be cancelled by admin.");
		}
		String c = comment != null ? comment.trim() : null;
		if (c != null && c.length() > 500) c = c.substring(0, 500);
		req.setStatus(VacationRequestStatus.ADMIN_CANCELLED);
		req.setReviewComment(c);
		req.setReviewedAt(Instant.now());
		req.setReviewedById(reviewerId);
		vacationRequestRepository.save(req);
	}

	@Transactional
	public void adminUpdateDates(long requestId, long reviewerId, List<LocalDate> newDates, String comment) {
		VacationRequest req = vacationRequestRepository.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("Vacation request not found: " + requestId));
		if (newDates == null) newDates = List.of();
		vacationRequestDayRepository.deleteAll(vacationRequestDayRepository.findByVacationRequestIdOrderByRequestDateAsc(req.getId()));
		for (LocalDate d : newDates) {
			if (d == null) continue;
			VacationRequestDay day = new VacationRequestDay();
			day.setVacationRequestId(req.getId());
			day.setRequestDate(d);
			vacationRequestDayRepository.save(day);
		}
		String c = comment != null ? comment.trim() : null;
		if (c != null && c.length() > 500) c = c.substring(0, 500);
		req.setStatus(VacationRequestStatus.ADMIN_UPDATED);
		req.setReviewComment(c);
		req.setReviewedAt(Instant.now());
		req.setReviewedById(reviewerId);
		req.setRejectionReason(null);
		vacationRequestRepository.save(req);
	}
}
