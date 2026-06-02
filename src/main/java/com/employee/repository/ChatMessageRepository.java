package com.employee.repository;

import com.employee.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	@Query("""
		SELECT m
		FROM ChatMessage m
		WHERE m.senderId = :userId OR m.receiverId = :userId
		ORDER BY m.timestamp DESC, m.id DESC
		""")
	List<ChatMessage> findRecentForUser(
			@Param("userId") Long userId,
			org.springframework.data.domain.Pageable pageable);

	@Query("""
		SELECT DISTINCT CASE WHEN m.senderId = :userId THEN m.receiverId ELSE m.senderId END
		FROM ChatMessage m
		WHERE m.senderId = :userId OR m.receiverId = :userId
		""")
	List<Long> findDistinctContactIds(@Param("userId") Long userId);

	@Query("""
		SELECT m
		FROM ChatMessage m
		WHERE (m.senderId = :userId AND m.receiverId = :contactId)
		   OR (m.senderId = :contactId AND m.receiverId = :userId)
		ORDER BY m.timestamp DESC, m.id DESC
		""")
	List<ChatMessage> findLatestBetween(
			@Param("userId") Long userId,
			@Param("contactId") Long contactId,
			org.springframework.data.domain.Pageable pageable);

	@Query("""
		SELECT m
		FROM ChatMessage m
		WHERE (m.senderId = :a AND m.receiverId = :b)
		   OR (m.senderId = :b AND m.receiverId = :a)
		ORDER BY m.timestamp ASC, m.id ASC
		""")
	List<ChatMessage> findConversation(@Param("a") Long a, @Param("b") Long b);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		UPDATE ChatMessage m
		SET m.readAt = :readAt
		WHERE m.receiverId = :recipientId
		  AND m.senderId = :senderId
		  AND m.readAt IS NULL
		""")
	int markConversationAsRead(
			@Param("recipientId") Long recipientId,
			@Param("senderId") Long senderId,
			@Param("readAt") Instant readAt);

	@Query("""
		SELECT m.senderId, COUNT(m)
		FROM ChatMessage m
		WHERE m.receiverId = :recipientId AND m.readAt IS NULL
		GROUP BY m.senderId
		""")
	List<Object[]> countUnreadBySender(@Param("recipientId") Long recipientId);

	@Query("""
		SELECT COUNT(m)
		FROM ChatMessage m
		WHERE m.receiverId = :recipientId AND m.readAt IS NULL
		""")
	long countUnreadForRecipient(@Param("recipientId") Long recipientId);
}

