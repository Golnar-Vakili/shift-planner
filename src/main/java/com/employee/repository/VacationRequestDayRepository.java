package com.employee.repository;

import com.employee.domain.VacationRequestDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VacationRequestDayRepository extends JpaRepository<VacationRequestDay, Long> {

	List<VacationRequestDay> findByVacationRequestIdOrderByRequestDateAsc(Long vacationRequestId);

	Optional<VacationRequestDay> findByVacationRequestIdAndRequestDate(Long vacationRequestId, LocalDate requestDate);

	void deleteByVacationRequestIdAndRequestDate(Long vacationRequestId, LocalDate requestDate);

	boolean existsByVacationRequestIdAndRequestDate(Long vacationRequestId, LocalDate requestDate);

	void deleteByVacationRequestId(Long vacationRequestId);
}
