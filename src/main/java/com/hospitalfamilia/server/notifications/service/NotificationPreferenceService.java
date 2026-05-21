package com.hospitalfamilia.server.notifications.service;

import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.notifications.dto.NotificationPreferenceDto;
import com.hospitalfamilia.server.notifications.dto.NotificationPreferenceUpdateRequest;
import com.hospitalfamilia.server.notifications.entity.NotificationPreference;
import com.hospitalfamilia.server.notifications.exception.NotificationPreferenceException;
import com.hospitalfamilia.server.notifications.repository.NotificationPreferenceRepository;
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
