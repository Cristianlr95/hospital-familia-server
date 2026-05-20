package com.hospitalfamilia.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hospitalfamilia.server.auth.entity.RoleName;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.events.repository.PatientEventRepository;
import com.hospitalfamilia.server.linking.entity.LinkStatus;
import com.hospitalfamilia.server.linking.repository.PatientRepository;
import com.hospitalfamilia.server.linking.repository.TutorPatientLinkRepository;
import com.hospitalfamilia.server.patientstatus.repository.PatientCareSnapshotRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "app.demo.seed-enabled=true",
    "spring.datasource.url=jdbc:h2:mem:hospital_familia_demo_seed_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class DemoDataSeederIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private TutorPatientLinkRepository tutorPatientLinkRepository;

    @Autowired
    private PatientCareSnapshotRepository patientCareSnapshotRepository;

    @Autowired
    private PatientEventRepository patientEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void seedCreatesDemoReviewDataWhenEnabled() {
        var tutor = userRepository.findByEmailIgnoreCase(DemoDataSeeder.TUTOR_EMAIL).orElseThrow();
        var staff = userRepository.findByEmailIgnoreCase(DemoDataSeeder.STAFF_EMAIL).orElseThrow();
        var patient = patientRepository.findByLinkCodeIgnoreCaseAndActiveTrue(DemoDataSeeder.PATIENT_LINK_CODE).orElseThrow();

        assertThat(passwordEncoder.matches(DemoDataSeeder.DEMO_PASSWORD, tutor.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(DemoDataSeeder.DEMO_PASSWORD, staff.getPasswordHash())).isTrue();
        assertThat(tutor.getRoles()).anySatisfy(role -> assertThat(role.getName()).isEqualTo(RoleName.TUTOR));
        assertThat(staff.getRoles()).anySatisfy(role -> assertThat(role.getName()).isEqualTo(RoleName.STAFF));

        var link = tutorPatientLinkRepository.findByTutorAndPatientPublicIdAndStatus(
            tutor,
            patient.getPublicId(),
            LinkStatus.APPROVED
        );
        assertThat(link).isPresent();
        assertThat(patientCareSnapshotRepository.findByPatient(patient)).isPresent();
        assertThat(patientEventRepository.findByPatientAndScheduledAtBetweenOrderByScheduledAtAsc(
            patient,
            Instant.now().minus(1, ChronoUnit.DAYS),
            Instant.now().plus(14, ChronoUnit.DAYS)
        )).hasSize(2);
    }
}
