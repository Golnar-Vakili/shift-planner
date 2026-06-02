package com.employee.controller.api;

import com.employee.dto.HolidayDto;
import com.employee.service.HolidayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

	private final HolidayService holidayService;

	public HolidayController(HolidayService holidayService) {
		this.holidayService = holidayService;
	}

	@GetMapping
	public ResponseEntity<List<HolidayDto>> getHolidays(
			@RequestParam(defaultValue = "2025") int year,
			@RequestParam(defaultValue = "DE") String region) {
		year = Math.max(2020, Math.min(2030, year));
		List<HolidayDto> holidays = holidayService.getHolidays(year, region);
		return ResponseEntity.ok(holidays);
	}
}
