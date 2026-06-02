package com.employee.service;

import com.employee.domain.*;
import com.employee.repository.EmployeeRepository;
import com.employee.repository.OvertimeRequestRecipientRepository;
import com.employee.repository.OvertimeRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OvertimeRequestService {

	private final OvertimeRequestRepository requestRepository;
	private final OvertimeRequestRecipientRepository recipientRepository;
	private final EmployeeRepository employeeRepository;
	private final NotificationService notificationService;

	public OvertimeRequestService(
			OvertimeRequestRepository requestRepository,
			OvertimeRequestRecipientRepository recipientRepository,
			EmployeeRepository employeeRepository,
			NotificationService notificationService
	) {
		this.requestRepository = requestRepository;
		this.recipientRepository = recipientRepository;
		this.employeeRepository = employeeRepository;
		this.notificationService = notificationService;
	}

	public record EmployeeOption(Long id, String name, String avatarUrl) {}
	public record RequestRow(Long id, String title, String message, LocalDate workDate, LocalTime startTime, LocalTime endTime,
	                         OvertimeRequestStatus status, Instant createdAt,
	                         int recipientsTotal, int pending, int accepted, int declined) {}
	public record RecipientRow(Long recipientId, Long employeeId, String employeeName, String avatarUrl,
	                           OvertimeRecipientStatus responseStatus, Instant respondedAt) {}
	public record InboxRow(Long recipientId, Long requestId, String title, String message, LocalDate workDate,
	                       LocalTime startTime, LocalTime endTime, OvertimeRequestStatus requestStatus,
	                       OvertimeRecipientStatus responseStatus, Instant createdAt, Instant respondedAt) {}

	public List<EmployeeOption> listEmployeeOptions() {
		return employeeRepository.findAll().stream()
				.sorted(Comparator.comparing(e -> {
					String n = (e.getDisplayName() != null && !e.getDisplayName().isBlank()) ? e.getDisplayName() : e.getUsername();
					return n == null ? "" : n.toLowerCase(Locale.ROOT);
				}))
				.map(e -> new EmployeeOption(
						e.getId(),
						(e.getDisplayName() != null && !e.getDisplayName().isBlank()) ? e.getDisplayName() : e.getUsername(),
						toAvatarUrl(e.getProfilePicturePath())
				))
				.toList();
	}

	@Transactional
	public Long createRequest(Long adminId, String title, String message, LocalDate workDate,
	                          LocalTime startTime, LocalTime endTime, List<Long> employeeIds) {
		String t = title == null ? "" : title.trim();
		if (t.isEmpty()) throw new IllegalArgumentException("title is required");
		if (workDate == null) throw new IllegalArgumentException("workDate is required");
		List<Long> ids = (employeeIds == null ? List.<Long>of() : employeeIds).stream()
				.filter(Objects::nonNull)
				.distinct()
				.toList();
		if (ids.isEmpty()) throw new IllegalArgumentException("select at least one employee");

		OvertimeRequest req = requestRepository.save(new OvertimeRequest(adminId, t, message, workDate, startTime, endTime));

		Set<Long> existing = new HashSet<>(employeeRepository.findAllById(ids).stream().map(Employee::getId).toList());
		List<OvertimeRequestRecipient> recipients = existing.stream()
				.filter(eid -> !eid.equals(adminId))
				.map(eid -> new OvertimeRequestRecipient(req.getId(), eid))
				.toList();
		if (recipients.isEmpty()) throw new IllegalArgumentException("no valid recipients");
		recipientRepository.saveAll(recipients);

		// Notify recipients (system bell) with a deep link to their overtime inbox
		for (OvertimeRequestRecipient r : recipients) {
			notificationService.notifyUser(
					r.getEmployeeId(),
					NotificationType.OVERTIME_REQUEST,
					"You received a new overtime request for " + workDate + ".",
					"/dashboard/overtime-requests"
			);
		}

		return req.getId();
	}

	public List<RequestRow> listRequestsForAdmin(Long adminId) {
		// Keep simple: show all requests (not only created-by), but still include per-request recipient counts.
		var requests = requestRepository.findAllByOrderByCreatedAtDesc();
		if (requests.isEmpty()) return List.of();

		return requests.stream().map(r -> {
			long total = recipientRepository.findByRequestIdOrderByIdAsc(r.getId()).size();
			int pending = (int) recipientRepository.countByRequestIdAndResponseStatus(r.getId(), OvertimeRecipientStatus.PENDING);
			int accepted = (int) recipientRepository.countByRequestIdAndResponseStatus(r.getId(), OvertimeRecipientStatus.ACCEPTED);
			int declined = (int) recipientRepository.countByRequestIdAndResponseStatus(r.getId(), OvertimeRecipientStatus.DECLINED);
			return new RequestRow(
					r.getId(), r.getTitle(), r.getMessage(), r.getWorkDate(), r.getStartTime(), r.getEndTime(),
					r.getStatus(), r.getCreatedAt(),
					(int) total, pending, accepted, declined
			);
		}).toList();
	}

	public Optional<RequestRow> getRequestRow(Long requestId) {
		return requestRepository.findById(requestId).map(r -> {
			long total = recipientRepository.findByRequestIdOrderByIdAsc(r.getId()).size();
			int pending = (int) recipientRepository.countByRequestIdAndResponseStatus(r.getId(), OvertimeRecipientStatus.PENDING);
			int accepted = (int) recipientRepository.countByRequestIdAndResponseStatus(r.getId(), OvertimeRecipientStatus.ACCEPTED);
			int declined = (int) recipientRepository.countByRequestIdAndResponseStatus(r.getId(), OvertimeRecipientStatus.DECLINED);
			return new RequestRow(
					r.getId(), r.getTitle(), r.getMessage(), r.getWorkDate(), r.getStartTime(), r.getEndTime(),
					r.getStatus(), r.getCreatedAt(),
					(int) total, pending, accepted, declined
			);
		});
	}

	public List<RecipientRow> listRecipients(Long requestId) {
		var recs = recipientRepository.findByRequestIdOrderByIdAsc(requestId);
		if (recs.isEmpty()) return List.of();
		Set<Long> ids = recs.stream().map(OvertimeRequestRecipient::getEmployeeId).collect(Collectors.toSet());
		Map<Long, Employee> emp = employeeRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(Employee::getId, e -> e));
		return recs.stream().map(r -> {
			Employee e = emp.get(r.getEmployeeId());
			String name = e == null ? ("User " + r.getEmployeeId()) :
					((e.getDisplayName() != null && !e.getDisplayName().isBlank()) ? e.getDisplayName() : e.getUsername());
			return new RecipientRow(r.getId(), r.getEmployeeId(), name, e == null ? null : toAvatarUrl(e.getProfilePicturePath()),
					r.getResponseStatus(), r.getRespondedAt());
		}).toList();
	}

	public List<InboxRow> listInbox(Long employeeId) {
		var recs = recipientRepository.findByEmployeeIdOrderByIdDesc(employeeId);
		if (recs.isEmpty()) return List.of();
		Set<Long> requestIds = recs.stream().map(OvertimeRequestRecipient::getRequestId).collect(Collectors.toSet());
		Map<Long, OvertimeRequest> reqMap = requestRepository.findAllById(requestIds).stream()
				.collect(Collectors.toMap(OvertimeRequest::getId, r -> r));
		return recs.stream().map(rr -> {
			OvertimeRequest r = reqMap.get(rr.getRequestId());
			if (r == null) return null;
			return new InboxRow(
					rr.getId(),
					r.getId(),
					r.getTitle(),
					r.getMessage(),
					r.getWorkDate(),
					r.getStartTime(),
					r.getEndTime(),
					r.getStatus(),
					rr.getResponseStatus(),
					r.getCreatedAt(),
					rr.getRespondedAt()
			);
		}).filter(Objects::nonNull).sorted((a, b) -> b.createdAt().compareTo(a.createdAt())).toList();
	}

	@Transactional
	public void respond(Long recipientId, Long employeeId, OvertimeRecipientStatus response) {
		OvertimeRequestRecipient rec = recipientRepository.findByIdAndEmployeeId(recipientId, employeeId)
				.orElseThrow(() -> new IllegalArgumentException("request not found"));
		if (rec.getResponseStatus() != OvertimeRecipientStatus.PENDING) return;
		rec.setResponseStatus(response);
		rec.setRespondedAt(Instant.now());
		recipientRepository.save(rec);
	}

	private static String toAvatarUrl(String profilePicturePath) {
		if (profilePicturePath == null) return null;
		String p = profilePicturePath.trim();
		if (p.isEmpty()) return null;
		return "/user-photos/" + p;
	}
}

