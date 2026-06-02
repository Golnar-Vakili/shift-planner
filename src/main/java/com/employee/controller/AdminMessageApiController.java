package com.employee.controller;

import com.employee.domain.ChatMessage;
import com.employee.repository.ChatMessageRepository;
import com.employee.repository.EmployeeRepository;
import com.employee.security.EmployeeUserDetails;
import com.employee.service.ChatMessageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin messaging APIs. Keeps messaging isolated under /admin/api.
 * Assumes /admin/** is protected by ROLE_ADMIN in security config.
 */
@RestController
@RequestMapping("/admin/api")
public class AdminMessageApiController {

	private final EmployeeRepository employeeRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final ChatMessageService chatMessageService;

	public AdminMessageApiController(EmployeeRepository employeeRepository,
	                                 ChatMessageRepository chatMessageRepository,
	                                 ChatMessageService chatMessageService) {
		this.employeeRepository = employeeRepository;
		this.chatMessageRepository = chatMessageRepository;
		this.chatMessageService = chatMessageService;
	}

	public record ContactSearchResult(Long id, String name, String avatarUrl) {}
	public record MessageDto(Long id, Long senderId, Long receiverId, String content, Instant timestamp) {}
	public record SendMessageRequest(Long receiverId, String content) {}
	public record ConversationDto(Long contactId, String name, String avatarUrl, String lastMessage, Instant lastTimestamp, Long lastMessageId, boolean incoming, long unreadCount) {}

	private static String toAvatarUrl(String profilePicturePath) {
		if (profilePicturePath == null) return null;
		String p = profilePicturePath.trim();
		if (p.isEmpty()) return null;
		return "/user-photos/" + p;
	}

	@GetMapping("/contacts/search")
	public List<ContactSearchResult> searchContacts(@RequestParam(name = "q", required = false) String query) {
		String q = query == null ? "" : query.trim();
		if (q.isEmpty()) return List.of();

		return employeeRepository
				.searchByDisplayNameOrUsername(q, PageRequest.of(0, 12))
				.stream()
				.map(e -> new ContactSearchResult(
						e.getId(),
						(e.getDisplayName() != null && !e.getDisplayName().isBlank()) ? e.getDisplayName() : e.getUsername()
						, toAvatarUrl(e.getProfilePicturePath())
				))
				.toList();
	}

	@PostMapping("/messages/read/{contactId}")
	public Map<String, Object> markConversationRead(@AuthenticationPrincipal EmployeeUserDetails userDetails,
	                                              @PathVariable("contactId") Long contactId) {
		Long me = userDetails.getEmployee().getId();
		int updated = chatMessageService.markConversationAsRead(me, contactId);
		return Map.of("updated", updated);
	}

	@GetMapping("/messages/history/{contactId}")
	public List<MessageDto> history(@AuthenticationPrincipal EmployeeUserDetails userDetails,
	                                @PathVariable("contactId") Long contactId) {
		Long me = userDetails.getEmployee().getId();
		if (contactId == null || contactId <= 0) return List.of();
		chatMessageService.markConversationAsRead(me, contactId);
		return chatMessageRepository.findConversation(me, contactId)
				.stream()
				.map(m -> new MessageDto(m.getId(), m.getSenderId(), m.getReceiverId(), m.getContent(), m.getTimestamp()))
				.toList();
	}

	@PostMapping("/messages/send")
	public MessageDto sendMessage(@AuthenticationPrincipal EmployeeUserDetails userDetails,
	                              @RequestBody SendMessageRequest req) {
		Long senderId = userDetails.getEmployee().getId();
		Long receiverId = req != null ? req.receiverId() : null;
		String content = req != null ? req.content() : null;
		content = content == null ? "" : content.trim();

		if (receiverId == null || receiverId <= 0) {
			throw new IllegalArgumentException("receiverId is required");
		}
		if (content.isEmpty()) {
			throw new IllegalArgumentException("content is required");
		}
		if (!employeeRepository.existsById(receiverId)) {
			throw new IllegalArgumentException("receiver not found");
		}
		if (receiverId.equals(senderId)) {
			throw new IllegalArgumentException("cannot message yourself");
		}

		ChatMessage saved = chatMessageRepository.save(new ChatMessage(senderId, receiverId, content, Instant.now()));
		return new MessageDto(saved.getId(), saved.getSenderId(), saved.getReceiverId(), saved.getContent(), saved.getTimestamp());
	}

	@GetMapping("/conversations")
	public List<ConversationDto> conversations(@AuthenticationPrincipal EmployeeUserDetails userDetails) {
		Long me = userDetails.getEmployee().getId();

		Map<Long, ChatMessage> latestByContact = chatMessageService.latestMessageByContact(me);
		if (latestByContact.isEmpty()) return List.of();

		Set<Long> contactIds = latestByContact.keySet();
		var contacts = employeeRepository.findAllById(contactIds);
		Map<Long, String> nameById = contacts.stream().collect(Collectors.toMap(
				e -> e.getId(),
				e -> (e.getDisplayName() != null && !e.getDisplayName().isBlank()) ? e.getDisplayName() : e.getUsername()
		));
		Map<Long, String> avatarById = contacts.stream().collect(Collectors.toMap(
				e -> e.getId(),
				e -> toAvatarUrl(e.getProfilePicturePath()),
				(a, b) -> a
		));
		Map<Long, Long> unreadBySender = chatMessageService.unreadCountsBySender(me);

		return latestByContact.entrySet().stream()
				.map(e -> {
					Long contactId = e.getKey();
					ChatMessage m = e.getValue();
					boolean incoming = Objects.equals(m.getReceiverId(), me);
					long unreadCount = unreadBySender.getOrDefault(contactId, 0L);
					String name = nameById.getOrDefault(contactId, "User " + contactId);
					String avatarUrl = avatarById.get(contactId);
					String last = m.getContent() != null ? m.getContent() : "";
					if (last.length() > 80) last = last.substring(0, 77) + "…";
					return new ConversationDto(contactId, name, avatarUrl, last, m.getTimestamp(), m.getId(), incoming, unreadCount);
				})
				.sorted((a, b) -> {
					Instant at = a.lastTimestamp();
					Instant bt = b.lastTimestamp();
					if (at == null && bt == null) return 0;
					if (at == null) return 1;
					if (bt == null) return -1;
					return bt.compareTo(at);
				})
				.toList();
	}
}

