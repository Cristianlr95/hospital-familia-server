package com.hospitalfamilia.server.notifications.entity;

import com.hospitalfamilia.server.auth.entity.User;
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
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "state_changes_enabled", nullable = false)
    private boolean stateChangesEnabled = true;

    @Column(name = "events_enabled", nullable = false)
    private boolean eventsEnabled = true;

    @Column(name = "linking_updates_enabled", nullable = false)
    private boolean linkingUpdatesEnabled = true;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private boolean quietHoursEnabled = false;

    @Column(name = "quiet_hours_start", length = 5)
    private String quietHoursStart;

    @Column(name = "quiet_hours_end", length = 5)
    private String quietHoursEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
    }

    public NotificationPreference(User user) {
        this.user = user;
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

    public User getUser() {
        return user;
    }

    public boolean isStateChangesEnabled() {
        return stateChangesEnabled;
    }

    public boolean isEventsEnabled() {
        return eventsEnabled;
    }

    public boolean isLinkingUpdatesEnabled() {
        return linkingUpdatesEnabled;
    }

    public boolean isQuietHoursEnabled() {
        return quietHoursEnabled;
    }

    public String getQuietHoursStart() {
        return quietHoursStart;
    }

    public String getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(
        boolean stateChangesEnabled,
        boolean eventsEnabled,
        boolean linkingUpdatesEnabled,
        boolean quietHoursEnabled,
        String quietHoursStart,
        String quietHoursEnd
    ) {
        this.stateChangesEnabled = stateChangesEnabled;
        this.eventsEnabled = eventsEnabled;
        this.linkingUpdatesEnabled = linkingUpdatesEnabled;
        this.quietHoursEnabled = quietHoursEnabled;
        this.quietHoursStart = quietHoursEnabled ? quietHoursStart : null;
        this.quietHoursEnd = quietHoursEnabled ? quietHoursEnd : null;
    }
}
