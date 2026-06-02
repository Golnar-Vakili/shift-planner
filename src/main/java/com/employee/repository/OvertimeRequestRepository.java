package com.employee.repository;

import com.employee.domain.OvertimeRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Long> {

	List<OvertimeRequest> findAllByOrderByCreatedAtDesc();

	@Query("""
		SELECT r
		FROM OvertimeRequest r
		WHERE r.createdBy = :adminId
		ORDER BY r.createdAt DESC
		""")
	List<OvertimeRequest> findByCreatedByOrderByCreatedAtDesc(Long adminId, Pageable pageable);
}

