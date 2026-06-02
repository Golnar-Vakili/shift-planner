package com.employee.security;

import com.employee.repository.EmployeeRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class EmployeeUserDetailsService implements UserDetailsService {

	private final EmployeeRepository employeeRepository;

	public EmployeeUserDetailsService(EmployeeRepository employeeRepository) {
		this.employeeRepository = employeeRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return employeeRepository.findByUsername(username)
				.map(EmployeeUserDetails::new)
				.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
	}
}
