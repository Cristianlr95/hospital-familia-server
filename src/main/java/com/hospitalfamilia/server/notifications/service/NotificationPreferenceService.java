package com.hospitalfamilia.server.notifications.service;

import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.notifications.dto.NotificationPreferenceDto;
import com.hospitalfamilia.server.notifications.dto.NotificationPreferenceUpdateRequest;
import com.hospitalfamilia.server.notifications.entity.NotificationPreference;
import com.hospitalfamilia.server.notifications.entity.NotificationType;
import com.hospitalfamilia.server.notifications.exception.NotificationPreferenceException;
import com.hospitalfamilia.server.notifications.repository.NotificationPreferenceRepository;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

    private final UserRepository userRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreferenceService(
        UserRepository userRepository,
        NotificationPreferenceRepository preferenceRepository
    ) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
    }

    @Transactional
    public NotificationPreferenceDto getPreferences(String userEmail) {
        User user = findUser(userEmail);
        NotificationPreference preference = preferenceRepository.findByUser(user)
            .orElseGet(() -> preferenceRepository.save(new NotificationPreference(user)));

        return toDto(preference);
    }

    @Transactional
    public NotificationPreferenceDto updatePreferences(String userEmail, NotificationPreferenceUpdateRequest request) {
        validateQuietHours(request);
        User user = findUser(userEmail);
        NotificationPreference preference = preferenceRepository.findByUser(user)
            .orElseGet(() -> new NotificationPreference(user));

        preference.update(
            request.stateChangesEnabled(),
            request.eventsEnabled(),
            request.linkingUpdatesEnabled(),
            request.quietHoursEnabled(),
            request.quietHoursStart(),
            request.quietHoursEnd()
        );

        return toDto(preferenceRepository.save(preference));
    }

    private User findUser(String userEmail) {
        return userRepository.findByEmailIgnoreCase(userEmail)
            .orElseThrow(() -> new NotificationPreferenceException("Usuario no encontrado"));
    }

    private void validateQuietHours(NotificationPreferenceUpdateRequest request) {
        if (!request.quietHoursEnabled()) {
            return;
        }
        if (request.quietHoursStart() == null || request.quietHoursEnd() == null) {
            throw new NotificationPreferenceException("Debes indicar inicio y termino de horario silencioso");
        }
    }

    @Transactional
    public boolean allows(User user, NotificationType type) {
        NotificationPreference preference = preferenceRepository.findByUser(user)
            .orElseGet(() -> preferenceRepository.save(new NotificationPreference(user)));

        boolean typeEnabled = switch (type) {
            case STATE_CHANGE -> preference.isStateChangesEnabled();
            case NEW_EVENT, EVENT_UPDATED -> preference.isEventsEnabled();
            case LINKING_APPROVED, LINKING_REJECTED, LINKING_REVOKED -> preference.isLinkingUpdatesEnabled();
        };

        return typeEnabled && !isInsideQuietHours(preference);
    }

    private boolean isInsideQuietHours(NotificationPreference preference) {
        if (!preference.isQuietHoursEnabled()
            || preference.getQuietHoursStart() == null
            || preference.getQuietHoursEnd() == null) {
            return false;
        }

        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(preference.getQuietHoursStart());
            LocalTime end = LocalTime.parse(preference.getQuietHoursEnd());

            if (start.equals(end)) {
                return true;
            }
            if (start.isBefore(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            }
            return !now.isBefore(start) || now.isBefore(end);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private NotificationPreferenceDto toDto(NotificationPreference preference) {
        return new NotificationPreferenceDto(
            preference.isStateChangesEnabled(),
            preference.isEventsEnabled(),
            preference.isLinkingUpdatesEnabled(),
            preference.isQuietHoursEnabled(),
            preference.getQuietHoursStart(),
            preference.getQuietHoursEnd(),
            preference.getUpdatedAt()
        );
    }
}
