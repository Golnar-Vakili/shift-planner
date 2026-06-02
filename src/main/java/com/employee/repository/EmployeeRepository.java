package com.employee.repository;

import com.employee.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

	Optional<Employee> findByUsername(String username);
	boolean existsByUsername(String username);
	boolean existsByAdminTrue();

	@Query("""
		SELECT e
		FROM Employee e
		WHERE (LOWER(COALESCE(e.displayName, '')) LIKE LOWER(CONCAT('%', :q, '%')))
		   OR (LOWER(e.username) LIKE LOWER(CONCAT('%', :q, '%')))
		ORDER BY COALESCE(e.displayName, e.username) ASC
		""")
	List<Employee> searchByDisplayNameOrUsername(@Param("q") String q, org.springframework.data.domain.Pageable pageable);
}
