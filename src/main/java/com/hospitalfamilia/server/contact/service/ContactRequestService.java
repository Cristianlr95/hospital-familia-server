package com.hospitalfamilia.server.contact.service;

import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.contact.dto.ContactRequestCreateRequest;
import com.hospitalfamilia.server.contact.dto.ContactRequestDto;
import com.hospitalfamilia.server.contact.dto.ContactRequestResolveRequest;
import com.hospitalfamilia.server.contact.entity.ContactRequest;
import com.hospitalfamilia.server.contact.entity.ContactRequestStatus;
import com.hospitalfamilia.server.contact.exception.ContactRequestException;
import com.hospitalfamilia.server.contact.repository.ContactRequestRepository;
import com.hospitalfamilia.server.linking.entity.LinkStatus;
import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.repository.TutorPatientLinkRepository;
import com.hospitalfamilia.server.notifications.entity.NotificationType;
import com.hospitalfamilia.server.notifications.service.NotificationCenterService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactRequestService {

    private final UserRepository userRepository;
    private final TutorPatientLinkRepository linkRepository;
    private final ContactRequestRepository contactRequestRepository;
    private final NotificationCenterService notificationCenterService;

    public ContactRequestService(
        UserRepository userRepository,
        TutorPatientLinkRepository linkRepository,
        ContactRequestRepository contactRequestRepository,
        NotificationCenterService notificationCenterService
    ) {
        this.userRepository = userRepository;
        this.linkRepository = linkRepository;
        this.contactRequestRepository = contactRequestRepository;
        this.notificationCenterService = notificationCenterService;
    }

    @Transactional
    public ContactRequestDto create(String tutorEmail, ContactRequestCreateRequest request) {
        User tutor = findUser(tutorEmail);
        Patient patient = linkRepository.findByTutorAndPatientPublicIdAndStatus(
                tutor,
                request.patientPublicId(),
                LinkStatus.APPROVED
            )
            .map(link -> link.getPatient())
            .orElseThrow(() -> new ContactRequestException("Paciente no vinculado o no aprobado para contacto"));

        ContactRequest contactRequest = contactRequestRepository.save(new ContactRequest(tutor, patient, cleanMessage(request.message())));
        return toDto(contactRequest);
    }

    @Transactional(readOnly = true)
    public List<ContactRequestDto> myRequests(String tutorEmail) {
        User tutor = findUser(tutorEmail);
        return contactRequestRepository.findByTutorOrderByCreatedAtDesc(tutor).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ContactRequestDto> openRequestsForStaff() {
        return contactRequestRepository.findByStatusOrderByCreatedAtDesc(ContactRequestStatus.OPEN).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public ContactRequestDto resolve(String staffEmail, Long requestId, ContactRequestResolveRequest request) {
        User staff = findUser(staffEmail);
        ContactRequest contactRequest = contactRequestRepository.findById(requestId)
            .orElseThrow(() -> new ContactRequestException("Solicitud de contacto no encontrada"));

        if (contactRequest.getStatus() != ContactRequestStatus.OPEN) {
            throw new ContactRequestException("La solicitud de contacto ya fue resuelta");
        }

        contactRequest.resolve(staff, cleanOptional(request.note()));
        ContactRequest saved = contactRequestRepository.save(contactRequest);
        notificationCenterService.notifyTutor(
            saved.getTutor(),
            saved.getPatient(),
            NotificationType.CONTACT_REQUEST_RESOLVED,
            "Solicitud revisada por staff",
            saved.getResolutionNote() == null ? "El equipo del hospital reviso tu solicitud de contacto." : saved.getResolutionNote()
        );
        return toDto(saved);
    }

    private User findUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ContactRequestException("Usuario no encontrado"));
    }

    private String cleanMessage(String message) {
        return message.trim();
    }

    private String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ContactRequestDto toDto(ContactRequest request) {
        User tutor = request.getTutor();
        User resolvedBy = request.getResolvedBy();
        Patient patient = request.getPatient();
        return new ContactRequestDto(
            request.getId(),
            request.getStatus(),
            patient.getPublicId(),
            patient.getDisplayName(),
            tutor.getEmail(),
            tutor.getFirstName() + " " + tutor.getLastName(),
            request.getMessage(),
            request.getResolutionNote(),
            resolvedBy == null ? null : resolvedBy.getFirstName() + " " + resolvedBy.getLastName(),
            request.getResolvedAt(),
            request.getCreatedAt(),
            request.getUpdatedAt()
        );
    }
}
