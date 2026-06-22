package com.homekept.technician;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Technician roster entry — links a user (TECHNICIAN role) to HR/cost data.
 *
 * <h2>MVP scope</h2>
 * <p>The two founders are the technicians. There are exactly two rows in this table
 * at launch, seeded via the admin onboarding endpoint
 * ({@code POST /api/admin/technicians}) — never via SQL migration, because user IDs
 * are not known at migration time.
 *
 * <h2>Unit economics</h2>
 * <p>{@code fullyLoadedHourlyCostCents} is the single most important field in this table.
 * Set it from day 1 with a notional value (even if it's an estimate) so per-visit unit
 * economics are real numbers from visit #1. Integer cents — never float.
 *
 * <h2>Deferred features (Stage 3)</h2>
 * <p>Technician regions ({@code technician_region}) and availability
 * ({@code technician_availability}) tables are Stage 3 (50+ customers). They are NOT
 * in this entity or the V7 migration. See arch doc §2.7.
 *
 * <p>{@code userId} is unique: one {@code technician_profile} row per user.
 */
@Entity
@Table(name = "technician_profile")
public class TechnicianProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK → users.id (identity domain). Unique: one profile per user.
     * Bare BIGINT — no JPA cross-domain FK.
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "employee_status", length = 50)
    private String employeeStatus;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    /**
     * Fully-loaded hourly cost in integer cents (salary + benefits + overhead).
     * Used for per-visit and per-subscriber unit economics. Never float.
     */
    @Column(name = "fully_loaded_hourly_cost_cents")
    private Integer fullyLoadedHourlyCostCents;

    @Column(name = "vehicle_info", columnDefinition = "TEXT")
    private String vehicleInfo;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TechnicianProfile() {}

    public TechnicianProfile(Long userId, String employeeStatus, LocalDate hireDate,
                              Integer fullyLoadedHourlyCostCents) {
        this.userId = userId;
        this.employeeStatus = employeeStatus;
        this.hireDate = hireDate;
        this.fullyLoadedHourlyCostCents = fullyLoadedHourlyCostCents;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getEmployeeStatus() { return employeeStatus; }
    public void setEmployeeStatus(String employeeStatus) { this.employeeStatus = employeeStatus; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
    public Integer getFullyLoadedHourlyCostCents() { return fullyLoadedHourlyCostCents; }
    public void setFullyLoadedHourlyCostCents(Integer fullyLoadedHourlyCostCents) {
        this.fullyLoadedHourlyCostCents = fullyLoadedHourlyCostCents;
    }
    public String getVehicleInfo() { return vehicleInfo; }
    public void setVehicleInfo(String vehicleInfo) { this.vehicleInfo = vehicleInfo; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
