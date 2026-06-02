package com.employee.service;

import com.employee.domain.IssueCategory;
import com.employee.domain.RequestStatus;
import com.employee.domain.SupportRequest;
import com.employee.repository.SupportRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportService {

	private final SupportRequestRepository supportRequestRepository;

	public SupportService(SupportRequestRepository supportRequestRepository) {
		this.supportRequestRepository = supportRequestRepository;
	}

	@Transactional
	public SupportRequest submitRequest(Long userId, String usernameInput, IssueCategory issueCategory, String message) {
		SupportRequest request = new SupportRequest(issueCategory, message != null ? message.trim() : "");
		request.setUserId(userId);
		if (usernameInput != null && !usernameInput.isBlank()) {
			request.setUsernameInput(usernameInput.trim());
		}
		request.setStatus(RequestStatus.OPEN);
		return supportRequestRepository.save(request);
	}
}
