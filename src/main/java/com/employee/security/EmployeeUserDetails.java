package com.employee.security;

import com.employee.domain.Employee;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Adapts {@link Employee} to Spring Security's {@link UserDetails}.
 * Grants {@code ROLE_USER} to all users and {@code ROLE_ADMIN} when {@link Employee#isAdmin()} is true.
 * Used for role-based redirect after login (USER → /dashboard, ADMIN → /admin/dashboard).
 */
public class EmployeeUserDetails implements UserDetails {

	private final Employee employee;

	public EmployeeUserDetails(Employee employee) {
		this.employee = employee;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		List<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
		if (employee.isAdmin()) {
			authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
		}
		return authorities;
	}

	@Override
	public String getPassword() {
		return employee.getPasswordHash();
	}

	@Override
	public String getUsername() {
		return employee.getUsername();
	}

	@Override
	public boolean isAccountNonExpired() { return true; }

	@Override
	public boolean isAccountNonLocked() { return true; }

	@Override
	public boolean isCredentialsNonExpired() { return true; }

	@Override
	public boolean isEnabled() { return true; }

	public Employee getEmployee() {
		return employee;
	}
}
