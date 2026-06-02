package com.employee.repository;

import com.employee.domain.AppNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

	@Query("""
		SELECT n
		FROM AppNotification n
		WHERE n.userId = :userId
		ORDER BY n.createdAt DESC, n.id DESC
		""")
	List<AppNotification> findRecentForUser(@Param("userId") Long userId, Pageable pageable);

	@Query("""
		SELECT COUNT(n)
		FROM AppNotification n
		WHERE n.userId = :userId AND n.readAt IS NULL
		""")
	long countUnreadForUser(@Param("userId") Long userId);

	@Modifying
	@Query("""
		UPDATE AppNotification n
		SET n.readAt = :readAt
		WHERE n.id = :id AND n.userId = :userId AND n.readAt IS NULL
		""")
	int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("readAt") Instant readAt);
}

