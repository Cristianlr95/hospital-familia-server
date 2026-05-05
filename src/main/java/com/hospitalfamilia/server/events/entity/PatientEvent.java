package com.hospitalfamilia.server.events.entity;

import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.linking.entity.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "patient_events")
public class PatientEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private PatientEventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PatientEventStatus status = PatientEventStatus.SCHEDULED;

    @Column(nullable = false, length = 140)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(length = 120)
    private String service;

    @Column(length = 120)
    private String location;

    @Column(name = "responsible_staff", length = 160)
    private String responsibleStaff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PatientEvent() {
    }

    public PatientEvent(
        Patient patient,
        PatientEventType type,
        String title,
        String description,
        Instant scheduledAt,
        Integer estimatedDurationMinutes,
        String service,
        String location,
        String responsibleStaff,
        User createdBy
    ) {
        this.patient = patient;
        this.type = type;
        this.title = title;
        this.description = description;
        this.scheduledAt = scheduledAt;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.service = service;
        this.location = location;
        this.responsibleStaff = responsibleStaff;
        this.createdBy = createdBy;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public PatientEventType getType() {
        return type;
    }

    public PatientEventStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Integer getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public String getService() {
        return service;
    }

    public String getLocation() {
        return location;
    }

    public String getResponsibleStaff() {
        return responsibleStaff;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(
        PatientEventType type,
        String title,
        String description,
        Instant scheduledAt,
        Integer estimatedDurationMinutes,
        String service,
        String location,
        String responsibleStaff
    ) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.scheduledAt = scheduledAt;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.service = service;
        this.location = location;
        this.responsibleStaff = responsibleStaff;
    }

    public void changeStatus(PatientEventStatus status) {
        this.status = status;
    }
}
