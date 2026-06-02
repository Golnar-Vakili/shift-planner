package com.employee.repository;

import com.employee.domain.OvertimeRecipientStatus;
import com.employee.domain.OvertimeRequestRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OvertimeRequestRecipientRepository extends JpaRepository<OvertimeRequestRecipient, Long> {

	List<OvertimeRequestRecipient> findByRequestIdOrderByIdAsc(Long requestId);

	List<OvertimeRequestRecipient> findByEmployeeIdOrderByIdDesc(Long employeeId);

	Optional<OvertimeRequestRecipient> findByIdAndEmployeeId(Long id, Long employeeId);

	long countByRequestIdAndResponseStatus(Long requestId, OvertimeRecipientStatus responseStatus);

	@Query("""
		SELECT MAX(r.respondedAt)
		FROM OvertimeRequestRecipient r
		WHERE r.requestId = :requestId
		""")
	Instant findLatestRespondedAt(@Param("requestId") Long requestId);
}

