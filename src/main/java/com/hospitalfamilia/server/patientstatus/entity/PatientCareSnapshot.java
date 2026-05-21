package com.hospitalfamilia.server.patientstatus.entity;

import com.hospitalfamilia.server.linking.entity.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "patient_care_snapshots")
public class PatientCareSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "care_status", nullable = false, length = 80)
    private String careStatus;

    @Column(name = "current_service", length = 120)
    private String currentService;

    @Column(name = "current_location", length = 120)
    private String currentLocation;

    @Column(length = 220)
    private String summary;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PatientCareSnapshot() {
    }

    public PatientCareSnapshot(Patient patient, String careStatus, String currentService, String currentLocation, String summary) {
        this.patient = patient;
        this.careStatus = careStatus;
        this.currentService = currentService;
        this.currentLocation = currentLocation;
        this.summary = summary;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Patient getPatient() {
        return patient;
    }

    public String getCareStatus() {
        return careStatus;
    }

    public String getCurrentService() {
        return currentService;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String careStatus, String currentService, String currentLocation, String summary) {
        this.careStatus = careStatus;
        this.currentService = currentService;
        this.currentLocation = currentLocation;
        this.summary = summary;
    }
}
