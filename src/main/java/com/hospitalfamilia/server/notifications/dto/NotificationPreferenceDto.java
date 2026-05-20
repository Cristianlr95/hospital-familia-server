package com.hospitalfamilia.server.notifications.dto;

import java.time.Instant;

public record NotificationPreferenceDto(
    boolean stateChangesEnabled,
    boolean eventsEnabled,
    boolean linkingUpdatesEnabled,
    boolean quietHoursEnabled,
    String quietHoursStart,
    String quietHoursEnd,
    Instant updatedAt
) {
}
