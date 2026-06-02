package com.employee.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "users", indexes = @Index(columnList = "username", unique = true))
public class Employee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "contract_type", nullable = false, length = 20)
	private ContractType contractType;

	@Column(name = "display_name", length = 200)
	private String displayName;

	@Column(name = "profile_picture_path", length = 500)
	private String profilePicturePath;

	/** Maximum allowed working hours per week (default from contract type). Used for shift planning validation. */
	@Column(name = "allowed_hours_per_week", precision = 5, scale = 2, nullable = false)
	private BigDecimal allowedHoursPerWeek;

	/** Maximum allowed working hours per month (mainly for Mini-job, 42h). Null for full-time/part-time. */
	@Column(name = "allowed_hours_per_month", precision = 6, scale = 2)
	private BigDecimal allowedHoursPerMonth;

	/** Annual vacation entitlement (working days per year). Default from contract type; override per employee if needed. */
	@Column(name = "allowed_vacation_days", nullable = false)
	private Integer allowedVacationDays = 20;

	/** If true, user has ROLE_ADMIN and is redirected to /admin/dashboard after login. */
	@Column(name = "admin", nullable = false)
	private boolean admin = false;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Employee() {}

	public Employee(String username, String passwordHash, ContractType contractType) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.contractType = contractType;
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		setDefaultAllowedHoursFromContractType();
	}

	@PreUpdate
	public void setUpdatedAt() {
		this.updatedAt = Instant.now();
	}

	public void setDefaultAllowedHoursFromContractType() {
		this.allowedHoursPerWeek = contractType != null ? contractType.getDefaultHoursPerWeek() : null;
		this.allowedHoursPerMonth = contractType != null ? contractType.getDefaultHoursPerMonth() : null;
		if (contractType != null && allowedVacationDays == null) {
			this.allowedVacationDays = contractType.getDefaultVacationDays();
		}
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getUsername() { return username; }
	public void setUsername(String username) { this.username = username; }
	public String getPasswordHash() { return passwordHash; }
	public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
	public ContractType getContractType() { return contractType; }
	public void setContractType(ContractType contractType) {
		this.contractType = contractType;
		if (contractType != null && allowedHoursPerWeek == null && allowedHoursPerMonth == null) {
			setDefaultAllowedHoursFromContractType();
		}
		if (contractType != null && allowedVacationDays == null) {
			this.allowedVacationDays = contractType.getDefaultVacationDays();
		}
	}
	public String getDisplayName() { return displayName; }
	public void setDisplayName(String displayName) { this.displayName = displayName; }
	public String getProfilePicturePath() { return profilePicturePath; }
	public void setProfilePicturePath(String profilePicturePath) { this.profilePicturePath = profilePicturePath; }
	public BigDecimal getAllowedHoursPerWeek() { return allowedHoursPerWeek; }
	public void setAllowedHoursPerWeek(BigDecimal allowedHoursPerWeek) { this.allowedHoursPerWeek = allowedHoursPerWeek; }
	public BigDecimal getAllowedHoursPerMonth() { return allowedHoursPerMonth; }
	public void setAllowedHoursPerMonth(BigDecimal allowedHoursPerMonth) { this.allowedHoursPerMonth = allowedHoursPerMonth; }
	public Integer getAllowedVacationDays() { return allowedVacationDays != null ? allowedVacationDays : 20; }
	public void setAllowedVacationDays(Integer allowedVacationDays) { this.allowedVacationDays = allowedVacationDays != null ? allowedVacationDays : 20; }
	public boolean isAdmin() { return admin; }
	public void setAdmin(boolean admin) { this.admin = admin; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
