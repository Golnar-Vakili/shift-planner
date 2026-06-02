package com.employee.dto;

import java.time.LocalDate;

/**
 * A public holiday for calendar display (configurable per region/year).
 */
public record HolidayDto(LocalDate date, String label, String region) {}
