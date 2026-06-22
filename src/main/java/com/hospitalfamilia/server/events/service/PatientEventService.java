package com.hospitalfamilia.server.events.service;

import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.events.dto.PatientEventCreateRequest;
import com.hospitalfamilia.server.events.dto.PatientEventDto;
import com.hospitalfamilia.server.events.dto.PatientEventStatusUpdateRequest;
import com.hospitalfamilia.server.events.dto.PatientEventUpdateRequest;
import com.hospitalfamilia.server.events.entity.PatientEvent;
import com.hospitalfamilia.server.events.entity.PatientEventStatus;
import com.hospitalfamilia.server.events.exception.EventException;
import com.hospitalfamilia.server.events.repository.PatientEventRepository;
import com.hospitalfamilia.server.linking.entity.LinkStatus;
import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.entity.TutorPatientLink;
import com.hospitalfamilia.server.linking.repository.PatientRepository;
import com.hospitalfamilia.server.linking.repository.TutorPatientLinkRepository;
import com.hospitalfamilia.server.notifications.entity.NotificationType;
import com.hospitalfamilia.server.notifications.service.NotificationCenterService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientEventService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final TutorPatientLinkRepository linkRepository;
    private final PatientEventRepository eventRepository;
    private final NotificationCenterService notificationCenterService;

    public PatientEventService(
        UserRepository userRepository,
        PatientRepository patientRepository,
        TutorPatientLinkRepository linkRepository,
        PatientEventRepository eventRepository,
        NotificationCenterService notificationCenterService
    ) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.linkRepository = linkRepository;
        this.eventRepository = eventRepository;
        this.notificationCenterService = notificationCenterService;
    }

    @Transactional(readOnly = true)
    public List<PatientEventDto> upcomingEventsForTutor(String tutorEmail, UUID patientPublicId) {
        return eventsForTutor(tutorEmail, patientPublicId, null, null);
    }

    @Transactional(readOnly = true)
    public List<PatientEventDto> eventsForTutor(
        String tutorEmail,
        UUID patientPublicId,
        String from,
        String to
    ) {
        Patient patient = findApprovedPatientForTutor(tutorEmail, patientPublicId);
        return events(patient, EventQueryRange.resolve(from, to)).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PatientEventDto> eventsForStaff(UUID patientPublicId) {
        return eventsForStaff(patientPublicId, null, null);
    }

    @Transactional(readOnly = true)
    public List<PatientEventDto> eventsForStaff(UUID patientPublicId, String from, String to) {
        Patient patient = findActivePatient(patientPublicId);
        return events(patient, EventQueryRange.resolve(from, to)).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public PatientEventDto createEvent(String staffEmail, PatientEventCreateRequest request) {
        Patient patient = findActivePatient(request.patientPublicId());
        User staff = findUser(staffEmail);
        PatientEvent event = new PatientEvent(
            patient,
            request.type(),
            cleanRequired(request.title()),
            cleanOptional(request.description()),
            request.scheduledAt(),
            request.estimatedDurationMinutes(),
            cleanOptional(request.service()),
            cleanOptional(request.location()),
            cleanOptional(request.responsibleStaff()),
            staff
        );
        PatientEvent savedEvent = eventRepository.save(event);
        notificationCenterService.notifyApprovedTutors(
            patient,
            NotificationType.NEW_EVENT,
            "Nuevo evento programado",
            savedEvent.getTitle() + " - " + savedEvent.getScheduledAt().truncatedTo(ChronoUnit.MINUTES)
        );
        return toDto(savedEvent);
    }

    @Transactional
    public PatientEventDto updateEvent(Long eventId, PatientEventUpdateRequest request) {
        PatientEvent event = findEvent(eventId);
        event.update(
            request.type(),
            cleanRequired(request.title()),
            cleanOptional(request.description()),
            request.scheduledAt(),
            request.estimatedDurationMinutes(),
            cleanOptional(request.service()),
            cleanOptional(request.location()),
            cleanOptional(request.responsibleStaff())
        );
        PatientEvent savedEvent = eventRepository.save(event);
        notificationCenterService.notifyApprovedTutors(
            savedEvent.getPatient(),
            NotificationType.EVENT_UPDATED,
            "Evento actualizado",
            savedEvent.getTitle() + " - " + savedEvent.getStatus().name()
        );
        return toDto(savedEvent);
    }

    @Transactional
    public PatientEventDto changeStatus(Long eventId, PatientEventStatusUpdateRequest request) {
        PatientEvent event = findEvent(eventId);
        event.changeStatus(request.status());
        PatientEvent savedEvent = eventRepository.save(event);
        notificationCenterService.notifyApprovedTutors(
            savedEvent.getPatient(),
            NotificationType.EVENT_UPDATED,
            "Estado de evento actualizado",
            savedEvent.getTitle() + " - " + savedEvent.getStatus().name()
        );
        return toDto(savedEvent);
    }

    @Transactional
    public PatientEventDto cancelEvent(Long eventId) {
        PatientEvent event = findEvent(eventId);
        event.changeStatus(PatientEventStatus.CANCELLED);
        PatientEvent savedEvent = eventRepository.save(event);
        notificationCenterService.notifyApprovedTutors(
            savedEvent.getPatient(),
            NotificationType.EVENT_UPDATED,
            "Evento cancelado",
            savedEvent.getTitle()
        );
        return toDto(savedEvent);
    }

    private List<PatientEvent> events(Patient patient, EventQueryRange range) {
        if (range.explicit()) {
            return eventRepository.findByPatientAndScheduledAtBetweenOrderByScheduledAtAscIdAsc(
                patient,
                range.from(),
                range.to()
            );
        }
        return eventRepository.findByPatientAndStatusNotAndScheduledAtBetweenOrderByScheduledAtAscIdAsc(
            patient,
            PatientEventStatus.CANCELLED,
            range.from(),
            range.to()
        );
    }

    private Patient findApprovedPatientForTutor(String tutorEmail, UUID patientPublicId) {
        User tutor = findUser(tutorEmail);
        TutorPatientLink link = linkRepository.findByTutorAndPatientPublicIdAndStatus(tutor, patientPublicId, LinkStatus.APPROVED)
            .orElseThrow(() -> new AccessDeniedException("Paciente no vinculado o sin autorizacion aprobada"));
        return link.getPatient();
    }

    private Patient findActivePatient(UUID patientPublicId) {
        Patient patient = patientRepository.findByPublicId(patientPublicId)
            .orElseThrow(() -> new EventException("Paciente no encontrado"));
        if (!patient.isActive()) {
            throw new EventException("Paciente no disponible");
        }
        return patient;
    }

    private User findUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new EventException("Usuario no encontrado"));
    }

    private PatientEvent findEvent(Long eventId) {
        return eventRepository.findById(eventId)
            .orElseThrow(() -> new EventException("Evento no encontrado"));
    }

    private PatientEventDto toDto(PatientEvent event) {
        Patient patient = event.getPatient();
        return new PatientEventDto(
            event.getId(),
            patient.getPublicId(),
            patient.getDisplayName(),
            event.getType(),
            event.getStatus(),
            event.getTitle(),
            event.getDescription(),
            event.getScheduledAt(),
            event.getEstimatedDurationMinutes(),
            event.getService(),
            event.getLocation(),
            event.getResponsibleStaff(),
            event.getCreatedAt(),
            event.getUpdatedAt()
        );
    }

    private String cleanRequired(String value) {
        return value.trim();
    }

    private String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
