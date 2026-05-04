package com.hospitalfamilia.server.linking;

import static org.hamcrest.Matchers.hasSize;
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
import com.hospitalfamilia.server.linking.dto.LinkDecisionRequest;
import com.hospitalfamilia.server.linking.dto.LinkRequestCreateRequest;
import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.repository.PatientRepository;
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
class LinkingFlowIntegrationTests {

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
    void tutorCanRequestLinkButDoesNotSeePatientUntilApproved() throws Exception {
        String tutorToken = registerAndLoginTutor("link-tutor-1@example.com");
        patientRepository.save(new Patient("HF-1001", "Paciente Administrativo 1001"));

        MvcResult requestResult = mockMvc.perform(post("/api/linking/request")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkRequestCreateRequest("HF-1001"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn();

        Long linkId = objectMapper.readTree(requestResult.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(get("/api/linking/my-patients")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));

        String staffToken = createStaffAndLogin("link-staff-1@example.com");
        mockMvc.perform(put("/api/linking/{id}/approve", linkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.patientDisplayName").value("Paciente Administrativo 1001"));

        mockMvc.perform(get("/api/linking/my-patients")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].displayName").value("Paciente Administrativo 1001"));
    }

    @Test
    void tutorCannotListPendingStaffQueue() throws Exception {
        String tutorToken = registerAndLoginTutor("link-tutor-2@example.com");

        mockMvc.perform(get("/api/linking/pending")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void staffCanListAndRejectPendingRequests() throws Exception {
        String tutorToken = registerAndLoginTutor("link-tutor-3@example.com");
        String staffToken = createStaffAndLogin("link-staff-3@example.com");
        patientRepository.save(new Patient("HF-1003", "Paciente Administrativo 1003"));

        MvcResult requestResult = mockMvc.perform(post("/api/linking/request")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkRequestCreateRequest("HF-1003"))))
            .andExpect(status().isCreated())
            .andReturn();
        Long linkId = objectMapper.readTree(requestResult.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(get("/api/linking/pending")
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].patientDisplayName").exists())
            .andExpect(jsonPath("$.data[0].tutorEmail").exists());

        LinkDecisionRequest decision = new LinkDecisionRequest("Documento no coincide");
        mockMvc.perform(put("/api/linking/{id}/reject", linkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(decision)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(get("/api/linking/my-requests")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].status").value("REJECTED"))
            .andExpect(jsonPath("$.data[0].decisionReason").value("Documento no coincide"));
    }

    @Test
    void invalidPatientCodeReturnsBadRequest() throws Exception {
        String tutorToken = registerAndLoginTutor("link-tutor-4@example.com");

        mockMvc.perform(post("/api/linking/request")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkRequestCreateRequest("NO-EXISTE"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void duplicateActiveRequestIsRejected() throws Exception {
        String tutorToken = registerAndLoginTutor("link-tutor-5@example.com");
        patientRepository.save(new Patient("HF-1005", "Paciente Administrativo 1005"));
        LinkRequestCreateRequest request = new LinkRequestCreateRequest("HF-1005");

        mockMvc.perform(post("/api/linking/request")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/linking/request")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Ya existe una solicitud para este paciente"));
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
        User staff = new User(email, passwordEncoder.encode("password123"), "Staff", "Prueba", null);
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
