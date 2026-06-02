package com.employee.repository;

import com.employee.domain.VacationRequest;
import com.employee.domain.VacationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VacationRequestRepository extends JpaRepository<VacationRequest, Long> {

	@Query("SELECT r FROM VacationRequest r WHERE r.deletedAt IS NULL AND r.employeeId = :employeeId AND r.year = :year")
	Optional<VacationRequest> findByEmployeeIdAndYear(Long employeeId, int year);

	/** Same employee+year including soft-deleted (at most one row due to DB unique key). Used to revive after admin soft-delete. */
	@Query("SELECT r FROM VacationRequest r WHERE r.employeeId = :employeeId AND r.year = :year")
	Optional<VacationRequest> findByEmployeeIdAndYearIncludingDeleted(Long employeeId, int year);

	@Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM VacationRequest r WHERE r.deletedAt IS NULL AND r.employeeId = :employeeId AND r.year = :year")
	boolean existsByEmployeeIdAndYear(Long employeeId, int year);

	@Query("SELECT r FROM VacationRequest r WHERE r.deletedAt IS NULL ORDER BY r.updatedAt DESC")
	List<VacationRequest> findAllByOrderByUpdatedAtDesc();

	long countByStatus(VacationRequestStatus status);
}
