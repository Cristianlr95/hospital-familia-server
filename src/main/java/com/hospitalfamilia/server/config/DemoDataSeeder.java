package com.hospitalfamilia.server.config;

import com.hospitalfamilia.server.auth.entity.Role;
import com.hospitalfamilia.server.auth.entity.RoleName;
import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.repository.RoleRepository;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.events.entity.PatientEvent;
import com.hospitalfamilia.server.events.entity.PatientEventType;
import com.hospitalfamilia.server.events.repository.PatientEventRepository;
import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.entity.TutorPatientLink;
import com.hospitalfamilia.server.linking.repository.PatientRepository;
import com.hospitalfamilia.server.linking.repository.TutorPatientLinkRepository;
import com.hospitalfamilia.server.patientstatus.entity.PatientCareSnapshot;
import com.hospitalfamilia.server.patientstatus.repository.PatientCareSnapshotRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.demo", name = "seed-enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String TUTOR_EMAIL = "familia.rivera@hospitalfamilia.local";
    public static final String STAFF_EMAIL = "enfermeria.central@hospitalfamilia.local";
    public static final String DEMO_PASSWORD = "password123";
    public static final String PATIENT_LINK_CODE = "HF-REV-001";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PatientRepository patientRepository;
    private final TutorPatientLinkRepository tutorPatientLinkRepository;
    private final PatientCareSnapshotRepository patientCareSnapshotRepository;
    private final PatientEventRepository patientEventRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
        UserRepository userRepository,
        RoleRepository roleRepository,
        PatientRepository patientRepository,
        TutorPatientLinkRepository tutorPatientLinkRepository,
        PatientCareSnapshotRepository patientCareSnapshotRepository,
        PatientEventRepository patientEventRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.patientRepository = patientRepository;
        this.tutorPatientLinkRepository = tutorPatientLinkRepository;
        this.patientCareSnapshotRepository = patientCareSnapshotRepository;
        this.patientEventRepository = patientEventRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User tutor = ensureUser(TUTOR_EMAIL, RoleName.TUTOR, "Camila", "Rivera", "+56941112233");
        User staff = ensureUser(STAFF_EMAIL, RoleName.STAFF, "Valentina", "Rios", "+56952223344");
        Patient patient = ensurePatient();

        ensureApprovedLink(tutor, staff, patient);
        ensureCareSnapshot(patient);
        ensurePatientEvents(patient, staff);
    }

    private User ensureUser(String email, RoleName roleName, String firstName, String lastName, String phoneNumber) {
        Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new IllegalStateException("Missing required role: " + roleName));

        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseGet(() -> new User(email, passwordEncoder.encode(DEMO_PASSWORD), firstName, lastName, phoneNumber));

        if (user.getRoles().stream().noneMatch(existingRole -> existingRole.getName() == roleName)) {
            user.addRole(role);
        }

        return userRepository.save(user);
    }

    private Patient ensurePatient() {
        return patientRepository.findByLinkCodeIgnoreCaseAndActiveTrue(PATIENT_LINK_CODE)
            .orElseGet(() -> patientRepository.save(new Patient(PATIENT_LINK_CODE, "Maria Gonzalez Rivera")));
    }

    private void ensureApprovedLink(User tutor, User staff, Patient patient) {
        if (tutorPatientLinkRepository.existsByTutorAndPatient(tutor, patient)) {
            return;
        }

        TutorPatientLink link = new TutorPatientLink(tutor, patient);
        link.approve(staff);
        tutorPatientLinkRepository.save(link);
    }

    private void ensureCareSnapshot(Patient patient) {
        patientCareSnapshotRepository.findByPatient(patient)
            .orElseGet(() -> patientCareSnapshotRepository.save(new PatientCareSnapshot(
                patient,
                "ESTABLE",
                "Medicina interna",
                "Piso 4 - Habitacion 412",
                "Paciente estable, con controles programados y acompanamiento familiar habilitado."
            )));
    }

    private void ensurePatientEvents(Patient patient, User staff) {
        Instant now = Instant.now();
        if (!patientEventRepository.findByPatientAndScheduledAtBetweenOrderByScheduledAtAsc(
            patient,
            now.minus(1, ChronoUnit.DAYS),
            now.plus(14, ChronoUnit.DAYS)
        ).isEmpty()) {
            return;
        }

        patientEventRepository.save(new PatientEvent(
            patient,
            PatientEventType.EXAM,
            "Examen de control",
            "Control de laboratorio y signos vitales para seguimiento del equipo clinico.",
            now.plus(1, ChronoUnit.DAYS),
            45,
            "Medicina interna",
            "Box de examenes",
            "Dra. Valentina Rios",
            staff
        ));

        patientEventRepository.save(new PatientEvent(
            patient,
            PatientEventType.VISIT,
            "Visita familiar autorizada",
            "Bloque de visita habilitado para tutor registrado.",
            now.plus(2, ChronoUnit.DAYS),
            60,
            "Hospitalizacion",
            "Piso 4 - Habitacion 412",
            "Equipo de enfermeria",
            staff
        ));
    }
}
