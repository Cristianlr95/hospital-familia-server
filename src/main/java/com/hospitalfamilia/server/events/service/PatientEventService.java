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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientEventService {

    private static final int DEFAULT_UPCOMING_DAYS = 30;

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final TutorPatientLinkRepository linkRepository;
    private final PatientEventRepository eventRepository;

    public PatientEventService(
        UserRepository userRepository,
        PatientRepository patientRepository,
        TutorPatientLinkRepository linkRepository,
        PatientEventRepository eventRepository
    ) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.linkRepository = linkRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public List<PatientEventDto> upcomingEventsForTutor(String tutorEmail, UUID patientPublicId) {
        Patient patient = findApprovedPatientForTutor(tutorEmail, patientPublicId);
        return upcomingEvents(patient).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PatientEventDto> eventsForStaff(UUID patientPublicId) {
        Patient patient = findActivePatient(patientPublicId);
        return upcomingEvents(patient).stream().map(this::toDto).toList();
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
        return toDto(eventRepository.save(event));
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
        return toDto(eventRepository.save(event));
    }

    @Transactional
    public PatientEventDto changeStatus(Long eventId, PatientEventStatusUpdateRequest request) {
        PatientEvent event = findEvent(eventId);
        event.changeStatus(request.status());
        return toDto(eventRepository.save(event));
    }

    @Transactional
    public PatientEventDto cancelEvent(Long eventId) {
        PatientEvent event = findEvent(eventId);
        event.changeStatus(PatientEventStatus.CANCELLED);
        return toDto(eventRepository.save(event));
    }

    private List<PatientEvent> upcomingEvents(Patient patient) {
        Instant from = Instant.now();
        Instant to = from.plus(DEFAULT_UPCOMING_DAYS, ChronoUnit.DAYS);
        return eventRepository.findByPatientAndScheduledAtBetweenOrderByScheduledAtAsc(patient, from, to);
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
