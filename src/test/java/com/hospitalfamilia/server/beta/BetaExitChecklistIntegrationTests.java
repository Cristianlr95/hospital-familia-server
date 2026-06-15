package com.hospitalfamilia.server.beta;

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
import com.hospitalfamilia.server.beta.dto.BetaExitCheckUpdateRequest;
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
class BetaExitChecklistIntegrationTests {

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
    void staffCanListAndCompleteBetaExitCheck() throws Exception {
        String staffToken = createStaffAndLogin("beta-exit-staff@example.com");

        MvcResult listResult = mockMvc.perform(get("/api/staff/beta-exit-checklist")
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalChecks").value(greaterThanOrEqualTo(10)))
            .andExpect(jsonPath("$.data.progressPercent").value(greaterThanOrEqualTo(0)))
            .andReturn();

        long checkId = objectMapper.readTree(listResult.getResponse().getContentAsString())
            .at("/data/checks/0/id")
            .asLong();

        BetaExitCheckUpdateRequest request = new BetaExitCheckUpdateRequest(true, "Validado en revision local.");
        mockMvc.perform(put("/api/staff/beta-exit-checklist/{id}", checkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.completedChecks").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.checks[0].completed").value(true))
            .andExpect(jsonPath("$.data.checks[0].notes").value("Validado en revision local."));
    }

    @Test
    void tutorCannotAccessBetaExitChecklist() throws Exception {
        String tutorToken = registerAndLoginTutor("beta-exit-tutor@example.com");

        mockMvc.perform(get("/api/staff/beta-exit-checklist")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isForbidden());
    }

    private String registerAndLoginTutor(String email) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(email, "password123", "password123", "Tutor", "Beta", null);
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());
        return login(email, "password123");
    }

    private String createStaffAndLogin(String email) throws Exception {
        var staffRole = roleRepository.findByName(RoleName.STAFF).orElseThrow();
        User staff = new User(email, passwordEncoder.encode("password123"), "Staff", "Beta", null);
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
