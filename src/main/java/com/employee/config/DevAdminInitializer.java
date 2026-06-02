package com.employee.config;

import com.employee.domain.ContractType;
import com.employee.domain.Employee;
import com.employee.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a default development admin account on startup if no admin user exists.
 * Username: admin, password: admin123. Does not overwrite existing admins.
 */
@Component
@Order(100)
public class DevAdminInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DevAdminInitializer.class);
	private static final String DEFAULT_ADMIN_USERNAME = "admin";
	private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

	private final EmployeeRepository employeeRepository;
	private final PasswordEncoder passwordEncoder;

	public DevAdminInitializer(EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder) {
		this.employeeRepository = employeeRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (employeeRepository.existsByAdminTrue()) {
			log.debug("At least one admin user already exists; skipping default admin creation.");
			return;
		}
		if (employeeRepository.existsByUsername(DEFAULT_ADMIN_USERNAME)) {
			log.warn("User '{}' already exists but is not an admin. Please set admin=1 for that user in the database if it should be the admin.", DEFAULT_ADMIN_USERNAME);
			return;
		}
		Employee admin = new Employee(
				DEFAULT_ADMIN_USERNAME,
				passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD),
				ContractType.FULL_TIME
		);
		admin.setAdmin(true);
		employeeRepository.save(admin);
		log.info("Created default development admin user: username='{}'. Change the password in production.", DEFAULT_ADMIN_USERNAME);
	}
}
