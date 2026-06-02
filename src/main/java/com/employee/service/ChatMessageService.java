package com.employee.service;

import com.employee.domain.ChatMessage;
import com.employee.repository.ChatMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ChatMessageService {

	private final ChatMessageRepository chatMessageRepository;

	public ChatMessageService(ChatMessageRepository chatMessageRepository) {
		this.chatMessageRepository = chatMessageRepository;
	}

	/**
	 * Marks all unread messages addressed to {@code recipientId} from {@code senderId} as read.
	 */
	@Transactional
	public int markConversationAsRead(Long recipientId, Long senderId) {
		if (recipientId == null || recipientId <= 0 || senderId == null || senderId <= 0) {
			return 0;
		}
		if (recipientId.equals(senderId)) {
			return 0;
		}
		return chatMessageRepository.markConversationAsRead(recipientId, senderId, Instant.now());
	}

	public Map<Long, Long> unreadCountsBySender(Long recipientId) {
		if (recipientId == null || recipientId <= 0) {
			return Map.of();
		}
		List<Object[]> rows = chatMessageRepository.countUnreadBySender(recipientId);
		Map<Long, Long> out = new HashMap<>();
		for (Object[] row : rows) {
			if (row == null || row.length < 2 || row[0] == null || row[1] == null) continue;
			Long senderId = ((Number) row[0]).longValue();
			Long count = ((Number) row[1]).longValue();
			if (count > 0) {
				out.put(senderId, count);
			}
		}
		return out;
	}

	public long countTotalUnread(Long recipientId) {
		if (recipientId == null || recipientId <= 0) {
			return 0L;
		}
		return chatMessageRepository.countUnreadForRecipient(recipientId);
	}

	/**
	 * Latest message per contact for the given user. Includes read and unread threads;
	 * read state does not affect visibility.
	 */
	public Map<Long, ChatMessage> latestMessageByContact(Long userId) {
		if (userId == null || userId <= 0) {
			return Map.of();
		}

		Map<Long, ChatMessage> latestByContact = new LinkedHashMap<>();
		List<ChatMessage> recent = chatMessageRepository.findRecentForUser(userId, PageRequest.of(0, 500));
		for (ChatMessage m : recent) {
			Long other = contactIdFor(userId, m);
			if (other == null) continue;
			latestByContact.putIfAbsent(other, m);
		}

		if (!latestByContact.isEmpty()) {
			return latestByContact;
		}

		for (Long contactId : chatMessageRepository.findDistinctContactIds(userId)) {
			if (contactId == null || contactId <= 0 || contactId.equals(userId)) continue;
			if (latestByContact.containsKey(contactId)) continue;
			chatMessageRepository.findLatestBetween(userId, contactId, PageRequest.of(0, 1))
					.stream()
					.findFirst()
					.ifPresent(m -> latestByContact.put(contactId, m));
		}
		return latestByContact;
	}

	private static Long contactIdFor(Long userId, ChatMessage m) {
		if (m == null || userId == null) return null;
		if (Objects.equals(m.getSenderId(), userId)) {
			return m.getReceiverId();
		}
		if (Objects.equals(m.getReceiverId(), userId)) {
			return m.getSenderId();
		}
		return null;
	}
}
