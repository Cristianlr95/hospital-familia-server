package com.hospitalfamilia.server.notifications;

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
import com.hospitalfamilia.server.notifications.dto.NotificationPreferenceUpdateRequest;
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
class NotificationPreferenceIntegrationTests {

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
    void tutorCanReadDefaultPreferencesAndUpdateThem() throws Exception {
        String tutorToken = registerAndLoginTutor("preferences-tutor-1@example.com");

        mockMvc.perform(get("/api/notifications/preferences")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stateChangesEnabled").value(true))
            .andExpect(jsonPath("$.data.eventsEnabled").value(true))
            .andExpect(jsonPath("$.data.linkingUpdatesEnabled").value(true))
            .andExpect(jsonPath("$.data.quietHoursEnabled").value(false));

        NotificationPreferenceUpdateRequest request = new NotificationPreferenceUpdateRequest(
            false,
            true,
            false,
            true,
            "22:00",
            "07:00"
        );

        mockMvc.perform(put("/api/notifications/preferences")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stateChangesEnabled").value(false))
            .andExpect(jsonPath("$.data.eventsEnabled").value(true))
            .andExpect(jsonPath("$.data.linkingUpdatesEnabled").value(false))
            .andExpect(jsonPath("$.data.quietHoursEnabled").value(true))
            .andExpect(jsonPath("$.data.quietHoursStart").value("22:00"))
            .andExpect(jsonPath("$.data.quietHoursEnd").value("07:00"));
    }

    @Test
    void staffCannotManageTutorNotificationPreferences() throws Exception {
        String staffToken = createStaffAndLogin("preferences-staff-1@example.com");

        mockMvc.perform(get("/api/notifications/preferences")
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void quietHoursRequiresStartAndEndWhenEnabled() throws Exception {
        String tutorToken = registerAndLoginTutor("preferences-tutor-2@example.com");
        NotificationPreferenceUpdateRequest request = new NotificationPreferenceUpdateRequest(
            true,
            true,
            true,
            true,
            null,
            "07:00"
        );

        mockMvc.perform(put("/api/notifications/preferences")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Debes indicar inicio y termino de horario silencioso"));
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
