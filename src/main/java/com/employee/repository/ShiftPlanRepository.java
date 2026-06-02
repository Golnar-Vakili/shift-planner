package com.employee.repository;

import com.employee.domain.ShiftPlan;
import com.employee.domain.ShiftPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShiftPlanRepository extends JpaRepository<ShiftPlan, Long> {

	@Query("SELECT p FROM ShiftPlan p WHERE p.deletedAt IS NULL AND p.userId = :userId AND p.periodStart BETWEEN :start AND :end ORDER BY p.periodStart ASC")
	List<ShiftPlan> findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc(Long userId, LocalDate start, LocalDate end);

	@Query("SELECT p FROM ShiftPlan p WHERE p.deletedAt IS NULL AND p.userId = :userId AND p.status = :status ORDER BY p.updatedAt DESC")
	List<ShiftPlan> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, ShiftPlanStatus status);

	@Query("SELECT p FROM ShiftPlan p WHERE p.deletedAt IS NULL ORDER BY p.updatedAt DESC")
	List<ShiftPlan> findAllByOrderByUpdatedAtDesc();

	@Query("SELECT p FROM ShiftPlan p WHERE p.deletedAt IS NULL AND p.status = :status ORDER BY p.updatedAt DESC")
	List<ShiftPlan> findAllByStatusOrderByUpdatedAtDesc(ShiftPlanStatus status);

	@Query("SELECT p FROM ShiftPlan p WHERE p.userId = :userId AND p.periodStart <= :date AND p.periodEnd >= :date ORDER BY p.updatedAt DESC")
	List<ShiftPlan> findByUserIdAndDateInPeriod(Long userId, LocalDate date);

	Optional<ShiftPlan> findFirstByDeletedAtIsNullAndUserIdAndStatusOrderByUpdatedAtDesc(Long userId, ShiftPlanStatus status);

	/** Plans that overlap the given range: periodStart <= rangeEnd and periodEnd >= rangeStart. */
	@Query("SELECT p FROM ShiftPlan p WHERE p.deletedAt IS NULL AND p.userId = :userId AND p.status = :status AND p.periodStart <= :rangeEnd AND p.periodEnd >= :rangeStart ORDER BY p.periodStart ASC")
	List<ShiftPlan> findByUserIdAndStatusAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByPeriodStartAsc(
			Long userId, ShiftPlanStatus status, LocalDate rangeEnd, LocalDate rangeStart);

	/** Plans (any status) that overlap the given range: periodStart <= rangeEnd and periodEnd >= rangeStart. */
	@Query("SELECT p FROM ShiftPlan p WHERE p.deletedAt IS NULL AND p.userId = :userId AND p.periodStart <= :rangeEnd AND p.periodEnd >= :rangeStart ORDER BY p.periodStart ASC")
	List<ShiftPlan> findByUserIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByPeriodStartAsc(
			Long userId, LocalDate rangeEnd, LocalDate rangeStart);

	long countByStatus(ShiftPlanStatus status);

	/** For admin/workforce: approved plans overlapping a date range. */
	@Query("SELECT p FROM ShiftPlan p WHERE p.deletedAt IS NULL AND p.status = :status AND p.periodStart <= :rangeEnd AND p.periodEnd >= :rangeStart ORDER BY p.periodStart ASC")
	List<ShiftPlan> findByStatusAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByPeriodStartAsc(
			ShiftPlanStatus status, LocalDate rangeEnd, LocalDate rangeStart);
}
