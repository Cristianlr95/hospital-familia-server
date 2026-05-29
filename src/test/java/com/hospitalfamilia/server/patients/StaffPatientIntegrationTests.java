package com.hospitalfamilia.server.patients;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.hospitalfamilia.server.linking.dto.LinkRequestCreateRequest;
import com.hospitalfamilia.server.patients.dto.StaffPatientCreateRequest;
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
class StaffPatientIntegrationTests {

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
    void staffCanCreateAndListPatientForTutorLinking() throws Exception {
        String staffToken = createStaffAndLogin("patient-staff-1@example.com");
        String tutorToken = registerAndLoginTutor("patient-tutor-1@example.com");
        StaffPatientCreateRequest request = new StaffPatientCreateRequest("Paciente Beta Uno", "hf-beta-001");

        mockMvc.perform(post("/api/staff/patients")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.displayName").value("Paciente Beta Uno"))
            .andExpect(jsonPath("$.data.linkCode").value("HF-BETA-001"))
            .andExpect(jsonPath("$.data.publicId").exists());

        mockMvc.perform(get("/api/staff/patients")
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].linkCode", hasItem("HF-BETA-001")));

        mockMvc.perform(post("/api/linking/request")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkRequestCreateRequest("HF-BETA-001"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void tutorCannotCreateOrListStaffPatients() throws Exception {
        String tutorToken = registerAndLoginTutor("patient-tutor-2@example.com");

        mockMvc.perform(get("/api/staff/patients")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isForbidden());

        StaffPatientCreateRequest request = new StaffPatientCreateRequest("Paciente Bloqueado", "HF-BLOCKED-001");
        mockMvc.perform(post("/api/staff/patients")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void duplicateLinkCodeIsRejected() throws Exception {
        String staffToken = createStaffAndLogin("patient-staff-2@example.com");
        StaffPatientCreateRequest request = new StaffPatientCreateRequest("Paciente Duplicado", "HF-DUP-001");

        mockMvc.perform(post("/api/staff/patients")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/staff/patients")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Ya existe un paciente con este codigo de vinculacion"));
    }

    @Test
    void staffCanDeactivatePatientAndBlockNewLinkingRequests() throws Exception {
        String staffToken = createStaffAndLogin("patient-staff-3@example.com");
        String tutorToken = registerAndLoginTutor("patient-tutor-3@example.com");
        StaffPatientCreateRequest request = new StaffPatientCreateRequest("Paciente Archivado", "HF-ARCH-001");

        MvcResult createResult = mockMvc.perform(post("/api/staff/patients")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();
        String publicId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .at("/data/publicId")
            .asText();

        mockMvc.perform(patch("/api/staff/patients/{publicId}/deactivate", publicId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(get("/api/staff/patients")
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].linkCode").value(org.hamcrest.Matchers.not(hasItem("HF-ARCH-001"))));

        mockMvc.perform(post("/api/linking/request")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkRequestCreateRequest("HF-ARCH-001"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Codigo de paciente invalido o no disponible"));
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
