package com.employee.service;

import com.employee.dto.HolidayDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides public holidays (configurable). Germany-wide for now; later can add Bundesland.
 */
@Service
public class HolidayService {

	private static final String REGION_DE = "DE";

	public List<HolidayDto> getHolidays(int year, String region) {
		if (region == null || !region.toUpperCase().startsWith("DE")) {
			region = REGION_DE;
		}
		List<HolidayDto> list = new ArrayList<>();
		// Fixed dates
		list.add(new HolidayDto(LocalDate.of(year, 1, 1), "Neujahr", region));
		list.add(new HolidayDto(LocalDate.of(year, 5, 1), "Tag der Arbeit", region));
		list.add(new HolidayDto(LocalDate.of(year, 10, 3), "Tag der Deutschen Einheit", region));
		list.add(new HolidayDto(LocalDate.of(year, 12, 25), "Erster Weihnachtsfeiertag", region));
		list.add(new HolidayDto(LocalDate.of(year, 12, 26), "Zweiter Weihnachtsfeiertag", region));
		// Variable (Easter-based)
		LocalDate easter = calculateEaster(year);
		list.add(new HolidayDto(easter.minusDays(2), "Karfreitag", region));
		list.add(new HolidayDto(easter.plusDays(1), "Ostermontag", region));
		list.add(new HolidayDto(easter.plusDays(39), "Christi Himmelfahrt", region));
		list.add(new HolidayDto(easter.plusDays(50), "Pfingstmontag", region));
		return list;
	}

	/** Anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
	private static LocalDate calculateEaster(int year) {
		int a = year % 19;
		int b = year / 100;
		int c = year % 100;
		int d = b / 4;
		int e = b % 4;
		int f = (b + 8) / 25;
		int g = (b - f + 1) / 3;
		int h = (19 * a + b - d - g + 15) % 30;
		int i = c / 4;
		int k = c % 4;
		int l = (32 + 2 * e + 2 * i - h - k) % 7;
		int m = (a + 11 * h + 22 * l) / 451;
		int month = (h + l - 7 * m + 114) / 31;
		int day = ((h + l - 7 * m + 114) % 31) + 1;
		return LocalDate.of(year, month, day);
	}
}
