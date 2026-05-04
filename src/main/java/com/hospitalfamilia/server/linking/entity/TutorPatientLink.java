package com.hospitalfamilia.server.linking.entity;

import com.hospitalfamilia.server.auth.entity.User;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "tutor_patient_links",
    uniqueConstraints = @UniqueConstraint(name = "uk_tutor_patient_link", columnNames = {"tutor_id", "patient_id"})
)
public class TutorPatientLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    private User tutor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LinkStatus status = LinkStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_user_id")
    private User decidedBy;

    @Column(name = "decision_reason", length = 280)
    private String decisionReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TutorPatientLink() {
    }

    public TutorPatientLink(User tutor, Patient patient) {
        this.tutor = tutor;
        this.patient = patient;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.requestedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getTutor() {
        return tutor;
    }

    public Patient getPatient() {
        return patient;
    }

    public LinkStatus getStatus() {
        return status;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void approve(User staffUser) {
        this.status = LinkStatus.APPROVED;
        this.decidedBy = staffUser;
        this.decisionReason = null;
        this.decidedAt = Instant.now();
    }

    public void reject(User staffUser, String reason) {
        this.status = LinkStatus.REJECTED;
        this.decidedBy = staffUser;
        this.decisionReason = reason;
        this.decidedAt = Instant.now();
    }

    public void revoke(User actor, String reason) {
        this.status = LinkStatus.REVOKED;
        this.decidedBy = actor;
        this.decisionReason = reason;
        this.decidedAt = Instant.now();
    }
}
