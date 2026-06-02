package com.employee.controller.api;

import com.employee.security.EmployeeUserDetails;
import com.employee.service.DashboardExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardExportController {

	private static final Logger log = LoggerFactory.getLogger(DashboardExportController.class);
	private final DashboardExportService dashboardExportService;

	public DashboardExportController(DashboardExportService dashboardExportService) {
		this.dashboardExportService = dashboardExportService;
	}

	@GetMapping(value = "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> exportPdf(@AuthenticationPrincipal EmployeeUserDetails user) {
		long employeeId = user.getEmployee().getId();
		log.info("PDF export requested: employeeId={}", employeeId);
		byte[] pdf = dashboardExportService.exportShiftAndHolidayPlanPdf(employeeId);
		log.info("PDF export generated: employeeId={} bytes={}", employeeId, pdf != null ? pdf.length : 0);

		String filename = "shift-holiday-plan-" + LocalDate.now() + ".pdf";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.contentType(MediaType.APPLICATION_PDF)
				.body(pdf);
	}
}

