package com.hospitalfamilia.server.beta.entity;

import com.hospitalfamilia.server.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "beta_exit_checks")
public class BetaExitCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "check_key", nullable = false, unique = true, length = 80)
    private String key;

    @Column(nullable = false, length = 160)
    private String label;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private boolean completed;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_user_id")
    private User completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BetaExitCheck() {
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public boolean isCompleted() {
        return completed;
    }

    public String getNotes() {
        return notes;
    }

    public User getCompletedBy() {
        return completedBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(boolean completed, String notes, User actor) {
        this.completed = completed;
        this.notes = notes;
        if (completed) {
            this.completedBy = actor;
            this.completedAt = Instant.now();
            return;
        }
        this.completedBy = null;
        this.completedAt = null;
    }
}
