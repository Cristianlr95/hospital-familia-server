package com.hospitalfamilia.server.activity;

import static org.hamcrest.Matchers.hasItem;
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
import com.hospitalfamilia.server.linking.dto.LinkDecisionRequest;
import com.hospitalfamilia.server.linking.dto.LinkRequestCreateRequest;
import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.repository.PatientRepository;
import java.time.Instant;
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
class ActivityFeedIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void tutorFeedReturnsLinkAndEventActivity() throws Exception {
        String tutorToken = registerAndLoginTutor("activity-tutor-1@example.com");
        String staffToken = createStaffAndLogin("activity-staff-1@example.com");
        patientRepository.save(new Patient("HF-ACT-1", "Paciente Actividad 1"));

        Long linkId = requestLink(tutorToken, "HF-ACT-1");
        approveLink(staffToken, linkId);

        mockMvc.perform(post("/api/events")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PatientEventCreateRequest(
                    findPatientPublicId("HF-ACT-1"),
                    PatientEventType.EXAM,
                    "Examen de control",
                    "Control programado",
                    Instant.now().plusSeconds(3600),
                    30,
                    "Imagenologia",
                    "Piso 2",
                    "Staff Uno"
                ))))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/activity/tutor")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].kind", hasItem("LINK")))
            .andExpect(jsonPath("$.data[*].kind", hasItem("EVENT")))
            .andExpect(jsonPath("$.data[*].patientDisplayName", hasItem("Paciente Actividad 1")));
    }

    @Test
    void staffFeedReturnsPendingAndHistoryActivity() throws Exception {
        String tutorToken = registerAndLoginTutor("activity-tutor-2@example.com");
        String staffToken = createStaffAndLogin("activity-staff-2@example.com");
        patientRepository.save(new Patient("HF-ACT-2", "Paciente Actividad 2"));

        Long linkId = requestLink(tutorToken, "HF-ACT-2");

        mockMvc.perform(get("/api/activity/staff")
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].kind", hasItem("LINK_PENDING")));

        mockMvc.perform(put("/api/linking/{id}/reject", linkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkDecisionRequest("Documento incompleto"))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/activity/staff")
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].kind", hasItem("LINK_HISTORY")))
            .andExpect(jsonPath("$.data[*].message", hasItem("Tutor Prueba - solicitud rechazada: Documento incompleto")));
    }

    private Long requestLink(String tutorToken, String patientCode) throws Exception {
        MvcResult requestResult = mockMvc.perform(post("/api/linking/request")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkRequestCreateRequest(patientCode))))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readTree(requestResult.getResponse().getContentAsString()).at("/data/id").asLong();
    }

    private void approveLink(String staffToken, Long linkId) throws Exception {
        mockMvc.perform(put("/api/linking/{id}/approve", linkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk());
    }

    private java.util.UUID findPatientPublicId(String patientCode) {
        return patientRepository.findByLinkCodeIgnoreCaseAndActiveTrue(patientCode).orElseThrow().getPublicId();
    }

    private String registerAndLoginTutor(String email) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(email, "password123", "password123", "Tutor", "Prueba", null);
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());
        return login(email, "password123");
    }

    private String createStaffAndLogin(String email) throws Exception {
        var staffRole = roleRepository.findByName(RoleName.STAFF).orElseThrow();
        User staff = new User(email, passwordEncoder.encode("password123"), "Staff", "Uno", null);
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
