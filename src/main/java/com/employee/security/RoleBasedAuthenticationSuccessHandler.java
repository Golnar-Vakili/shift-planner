package com.employee.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Collection;

/**
 * Redirects after successful form login by role:
 * ROLE_ADMIN → /admin/dashboard, otherwise → /dashboard.
 */
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	private static final String ADMIN_DASHBOARD = "/admin/dashboard";
	private static final String USER_DASHBOARD = "/dashboard";
	private static final String ROLE_ADMIN = "ROLE_ADMIN";

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request,
	                                    HttpServletResponse response,
	                                    Authentication authentication) throws IOException {
		boolean isAdmin = hasAuthority(authentication.getAuthorities(), ROLE_ADMIN);
		String target = isAdmin ? ADMIN_DASHBOARD : USER_DASHBOARD;
		response.sendRedirect(request.getContextPath() + target);
	}

	private static boolean hasAuthority(Collection<? extends GrantedAuthority> authorities, String role) {
		return authorities != null && authorities.stream()
				.map(GrantedAuthority::getAuthority)
				.anyMatch(role::equals);
	}
}
