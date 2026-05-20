package com.hospitalfamilia.server.notifications.dto;

import jakarta.validation.constraints.Pattern;

public record NotificationPreferenceUpdateRequest(
    boolean stateChangesEnabled,
    boolean eventsEnabled,
    boolean linkingUpdatesEnabled,
    boolean quietHoursEnabled,
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "La hora de inicio debe tener formato HH:mm")
    String quietHoursStart,
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "La hora de termino debe tener formato HH:mm")
    String quietHoursEnd
) {
}
