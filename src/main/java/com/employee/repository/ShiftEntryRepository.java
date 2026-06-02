package com.employee.repository;

import com.employee.domain.ShiftEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ShiftEntryRepository extends JpaRepository<ShiftEntry, Long> {

	List<ShiftEntry> findByPlanIdOrderByEntryDateAscStartTimeAsc(Long planId);

	List<ShiftEntry> findByPlanIdAndEntryDateBetweenOrderByEntryDateAscStartTimeAsc(Long planId, LocalDate start, LocalDate end);

	void deleteByPlanId(Long planId);
}
