package com.employee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;

/**
 * Configurable preset for Morning and Night shifts. Can be overridden in application.properties.
 */
@Configuration
@ConfigurationProperties(prefix = "shifts.presets")
public class ShiftPresetConfig {

	/** Morning shift: 08:00–16:00 (8h). */
	private LocalTime morningStart = LocalTime.of(8, 0);
	private LocalTime morningEnd = LocalTime.of(16, 0);

	/** Night shift: 22:00–06:00 (8h, end next day). */
	private LocalTime nightStart = LocalTime.of(22, 0);
	private LocalTime nightEnd = LocalTime.of(6, 0);

	public LocalTime getMorningStart() { return morningStart; }
	public void setMorningStart(LocalTime morningStart) { this.morningStart = morningStart; }
	public LocalTime getMorningEnd() { return morningEnd; }
	public void setMorningEnd(LocalTime morningEnd) { this.morningEnd = morningEnd; }
	public LocalTime getNightStart() { return nightStart; }
	public void setNightStart(LocalTime nightStart) { this.nightStart = nightStart; }
	public LocalTime getNightEnd() { return nightEnd; }
	public void setNightEnd(LocalTime nightEnd) { this.nightEnd = nightEnd; }
}
