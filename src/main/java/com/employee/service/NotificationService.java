package com.employee.service;

import com.employee.domain.AppNotification;
import com.employee.domain.NotificationType;
import com.employee.repository.AppNotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

	private final AppNotificationRepository notificationRepository;

	public NotificationService(AppNotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	@Transactional
	public void notifyUser(Long userId, NotificationType type, String message, String link) {
		if (userId == null || userId <= 0) return;
		String msg = message == null ? "" : message.trim();
		if (msg.isEmpty()) return;
		if (msg.length() > 500) msg = msg.substring(0, 500);
		String l = link != null && !link.isBlank() ? link.trim() : null;
		if (l != null && l.length() > 300) l = l.substring(0, 300);
		notificationRepository.save(new AppNotification(userId, type != null ? type : NotificationType.SYSTEM, msg, l));
	}

	public record NotificationDto(Long id, String type, String message, String link, Instant createdAt, boolean read) {}
	public record NotificationFeed(long unreadCount, List<NotificationDto> items) {}

	public NotificationFeed getFeed(Long userId, int limit) {
		int l = Math.max(1, Math.min(limit, 20));
		long unread = notificationRepository.countUnreadForUser(userId);
		var items = notificationRepository.findRecentForUser(userId, PageRequest.of(0, l)).stream()
				.map(n -> new NotificationDto(
						n.getId(),
						n.getType() != null ? n.getType().name() : NotificationType.SYSTEM.name(),
						n.getMessage(),
						n.getLink(),
						n.getCreatedAt(),
						n.getReadAt() != null
				))
				.toList();
		return new NotificationFeed(unread, items);
	}

	@Transactional
	public void markRead(Long notificationId, Long userId) {
		if (notificationId == null || userId == null) return;
		notificationRepository.markRead(notificationId, userId, Instant.now());
	}
}

