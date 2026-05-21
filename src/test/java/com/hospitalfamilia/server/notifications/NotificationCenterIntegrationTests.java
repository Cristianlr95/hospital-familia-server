package com.hospitalfamilia.server.notifications;

import static org.hamcrest.Matchers.hasItem;
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
import com.hospitalfamilia.server.linking.dto.LinkRequestCreateRequest;
import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.repository.PatientRepository;
import com.hospitalfamilia.server.notifications.dto.NotificationPreferenceUpdateRequest;
import com.hospitalfamilia.server.patientstatus.dto.PatientStatusUpdateRequest;
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
class NotificationCenterIntegrationTests {

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
    void tutorReceivesAndReadsLinkingAndStatusNotifications() throws Exception {
        String tutorToken = registerAndLoginTutor("center-tutor-1@example.com");
        String staffToken = createStaffAndLogin("center-staff-1@example.com");
        Patient patient = patientRepository.save(new Patient("HF-CENTER-1", "Paciente Centro 1"));

        Long linkId = requestLinkId(tutorToken, "HF-CENTER-1");
        mockMvc.perform(put("/api/linking/{id}/approve", linkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk());

        updateStatus(staffToken, patient, "Estable con seguimiento");

        MvcResult notificationsResult = mockMvc.perform(get("/api/notifications")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].type", hasItem("LINKING_APPROVED")))
            .andExpect(jsonPath("$.data[*].type", hasItem("STATE_CHANGE")))
            .andExpect(jsonPath("$.data[0].read").value(false))
            .andReturn();

        Long notificationId = objectMapper.readTree(notificationsResult.getResponse().getContentAsString())
            .at("/data/0/id")
            .asLong();

        mockMvc.perform(put("/api/notifications/{id}/read", notificationId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(notificationId))
            .andExpect(jsonPath("$.data.read").value(true))
            .andExpect(jsonPath("$.data.readAt").exists());
    }

    @Test
    void disabledStateChangePreferenceSuppressesStatusNotifications() throws Exception {
        String tutorToken = registerAndLoginTutor("center-tutor-2@example.com");
        String staffToken = createStaffAndLogin("center-staff-2@example.com");
        Patient patient = patientRepository.save(new Patient("HF-CENTER-2", "Paciente Centro 2"));

        Long linkId = requestLinkId(tutorToken, "HF-CENTER-2");
        mockMvc.perform(put("/api/linking/{id}/approve", linkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk());

        NotificationPreferenceUpdateRequest preferences = new NotificationPreferenceUpdateRequest(
            false,
            true,
            true,
            false,
            null,
            null
        );
        mockMvc.perform(put("/api/notifications/preferences")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(preferences)))
            .andExpect(status().isOk());

        updateStatus(staffToken, patient, "Cambio no notificado");

        mockMvc.perform(get("/api/notifications")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].type").value("LINKING_APPROVED"));
    }

    @Test
    void staffCannotReadTutorNotificationCenter() throws Exception {
        String staffToken = createStaffAndLogin("center-staff-3@example.com");

        mockMvc.perform(get("/api/notifications")
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isForbidden());
    }

    private void updateStatus(String staffToken, Patient patient, String careStatus) throws Exception {
        PatientStatusUpdateRequest request = new PatientStatusUpdateRequest(
            careStatus,
            "Medicina interna",
            "Habitacion 210",
            "Resumen visible para familia"
        );

        mockMvc.perform(put("/api/patients/{patientPublicId}/status", patient.getPublicId())
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    private Long requestLinkId(String tutorToken, String patientCode) throws Exception {
        MvcResult requestResult = mockMvc.perform(post("/api/linking/request")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkRequestCreateRequest(patientCode))))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readTree(requestResult.getResponse().getContentAsString()).at("/data/id").asLong();
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
