package com.hospitalfamilia.server.review;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospitalfamilia.server.auth.dto.LoginRequest;
import com.hospitalfamilia.server.auth.dto.RegisterRequest;
import com.hospitalfamilia.server.auth.entity.RoleName;
import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.repository.RoleRepository;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.events.dto.PatientEventCreateRequest;
import com.hospitalfamilia.server.events.entity.PatientEventType;
import com.hospitalfamilia.server.linking.dto.LinkRequestCreateRequest;
import com.hospitalfamilia.server.patients.dto.StaffPatientCreateRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewReadinessIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void staffCanReadBetaReadinessWithOperationalEvidence() throws Exception {
        String staffToken = createStaffAndLogin("review-staff@example.com");
        String tutorToken = registerAndLoginTutor("review-tutor@example.com");

        UUID patientPublicId = createPatient(staffToken, "Paciente Revision", "HF-READY-001");

        MvcResult linkResult = mockMvc.perform(post("/api/linking/request")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkRequestCreateRequest("HF-READY-001"))))
            .andExpect(status().isCreated())
            .andReturn();
        long linkId = objectMapper.readTree(linkResult.getResponse().getContentAsString())
            .at("/data/id")
            .asLong();

        mockMvc.perform(put("/api/linking/{id}/approve", linkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk());

        PatientEventCreateRequest eventRequest = new PatientEventCreateRequest(
            patientPublicId,
            PatientEventType.EXAM,
            "Control beta",
            "Evento de validacion",
            Instant.now().plusSeconds(3600),
            45,
            "Imagenologia",
            "Piso 2",
            "Equipo beta"
        );
        mockMvc.perform(post("/api/events")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(eventRequest)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/staff/review-readiness")
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.activePatientCount").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.approvedLinkCount").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.upcomingEventCount").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.unreadNotificationCount").value(greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.data.passedChecks").value(5))
            .andExpect(jsonPath("$.data.checks[*].key", hasItem("upcoming-events")));
    }

    @Test
    void tutorCannotReadStaffReadiness() throws Exception {
        String tutorToken = registerAndLoginTutor("review-tutor-blocked@example.com");

        mockMvc.perform(get("/api/staff/review-readiness")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isForbidden());
    }

    private UUID createPatient(String staffToken, String displayName, String linkCode) throws Exception {
        StaffPatientCreateRequest request = new StaffPatientCreateRequest(displayName, linkCode);
        MvcResult result = mockMvc.perform(post("/api/staff/patients")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
            .at("/data/publicId")
            .asText());
    }

    private String registerAndLoginTutor(String email) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(email, "password123", "password123", "Tutor", "Revision", null);
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());
        return login(email, "password123");
    }

    private String createStaffAndLogin(String email) throws Exception {
        var staffRole = roleRepository.findByName(RoleName.STAFF).orElseThrow();
        User staff = new User(email, passwordEncoder.encode("password123"), "Staff", "Revision", null);
        staff.addRole(staffRole);
        userRepository.save(staff);
        return login(email, "password123");
    }

    private String login(String email, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return loginJson.at("/data/accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
