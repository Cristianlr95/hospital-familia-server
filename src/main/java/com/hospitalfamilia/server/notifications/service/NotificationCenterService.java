package com.hospitalfamilia.server.notifications.service;

import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.linking.entity.LinkStatus;
import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.entity.TutorPatientLink;
import com.hospitalfamilia.server.linking.repository.TutorPatientLinkRepository;
import com.hospitalfamilia.server.notifications.dto.NotificationDto;
import com.hospitalfamilia.server.notifications.entity.Notification;
import com.hospitalfamilia.server.notifications.entity.NotificationType;
import com.hospitalfamilia.server.notifications.exception.NotificationPreferenceException;
import com.hospitalfamilia.server.notifications.repository.NotificationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationCenterService {

    private final UserRepository userRepository;
    private final TutorPatientLinkRepository linkRepository;
    private final NotificationPreferenceService preferenceService;
    private final NotificationRepository notificationRepository;

    public NotificationCenterService(
        UserRepository userRepository,
        TutorPatientLinkRepository linkRepository,
        NotificationPreferenceService preferenceService,
        NotificationRepository notificationRepository
    ) {
        this.userRepository = userRepository;
        this.linkRepository = linkRepository;
        this.preferenceService = preferenceService;
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> myNotifications(String userEmail) {
        User user = findUser(userEmail);
        return notificationRepository.findTop30ByRecipientOrderByCreatedAtDesc(user).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public NotificationDto markRead(String userEmail, Long notificationId) {
        User user = findUser(userEmail);
        Notification notification = notificationRepository.findByIdAndRecipient(notificationId, user)
            .orElseThrow(() -> new NotificationPreferenceException("Notificacion no encontrada"));
        notification.markRead();
        return toDto(notificationRepository.save(notification));
    }

    @Transactional
    public void notifyApprovedTutors(Patient patient, NotificationType type, String title, String message) {
        linkRepository.findByPatientAndStatus(patient, LinkStatus.APPROVED).stream()
            .map(TutorPatientLink::getTutor)
            .filter(tutor -> preferenceService.allows(tutor, type))
            .forEach(tutor -> notificationRepository.save(new Notification(tutor, patient, type, title, message)));
    }

    @Transactional
    public void notifyTutor(User tutor, Patient patient, NotificationType type, String title, String message) {
        if (preferenceService.allows(tutor, type)) {
            notificationRepository.save(new Notification(tutor, patient, type, title, message));
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new NotificationPreferenceException("Usuario no encontrado"));
    }

    private NotificationDto toDto(Notification notification) {
        Patient patient = notification.getPatient();
        return new NotificationDto(
            notification.getId(),
            notification.getType(),
            patient == null ? null : patient.getPublicId(),
            patient == null ? null : patient.getDisplayName(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getReadAt() != null,
            notification.getReadAt(),
            notification.getCreatedAt()
        );
    }
}
