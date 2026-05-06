package com.hospitalfamilia.server.events;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.hospitalfamilia.server.events.dto.PatientEventStatusUpdateRequest;
import com.hospitalfamilia.server.events.dto.PatientEventUpdateRequest;
import com.hospitalfamilia.server.events.entity.PatientEventStatus;
import com.hospitalfamilia.server.events.entity.PatientEventType;
import com.hospitalfamilia.server.linking.dto.LinkRequestCreateRequest;
import com.hospitalfamilia.server.linking.entity.Patient;
import com.hospitalfamilia.server.linking.repository.PatientRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class PatientEventIntegrationTests {

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
    void tutorSeesEventsOnlyAfterApprovedLink() throws Exception {
        String tutorToken = registerAndLoginTutor("event-tutor-1@example.com");
        String staffToken = createStaffAndLogin("event-staff-1@example.com");
        Patient patient = patientRepository.save(new Patient("HF-EVENT-1", "Paciente Evento 1"));

        createEvent(staffToken, patient, "Examen de laboratorio", PatientEventType.EXAM);
        MvcResult requestResult = requestLink(tutorToken, "HF-EVENT-1");
        Long linkId = objectMapper.readTree(requestResult.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(get("/api/patients/{patientPublicId}/events", patient.getPublicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(put("/api/linking/{id}/approve", linkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/patients/{patientPublicId}/events", patient.getPublicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].patientDisplayName").value("Paciente Evento 1"))
            .andExpect(jsonPath("$.data[0].type").value("EXAM"))
            .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"))
            .andExpect(jsonPath("$.data[0].title").value("Examen de laboratorio"));
    }

    @Test
    void staffCanCreateUpdateAndCancelEvent() throws Exception {
        String staffToken = createStaffAndLogin("event-staff-2@example.com");
        Patient patient = patientRepository.save(new Patient("HF-EVENT-2", "Paciente Evento 2"));

        MvcResult createResult = createEvent(staffToken, patient, "Visita familiar", PatientEventType.VISIT);
        Long eventId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asLong();

        PatientEventUpdateRequest updateRequest = new PatientEventUpdateRequest(
            PatientEventType.SURGERY,
            "Procedimiento programado",
            "Ingreso a pabellon informado",
            Instant.now().plus(2, ChronoUnit.DAYS),
            90,
            "Cirugia",
            "Pabellon 3",
            "Equipo quirurgico"
        );
        mockMvc.perform(put("/api/events/{id}", eventId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.type").value("SURGERY"))
            .andExpect(jsonPath("$.data.title").value("Procedimiento programado"));

        mockMvc.perform(put("/api/events/{id}/status", eventId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PatientEventStatusUpdateRequest(PatientEventStatus.IN_PROGRESS))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        mockMvc.perform(delete("/api/events/{id}", eventId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void tutorCannotCreateEventsAndStaffCanListPatientEvents() throws Exception {
        String tutorToken = registerAndLoginTutor("event-tutor-2@example.com");
        String staffToken = createStaffAndLogin("event-staff-3@example.com");
        Patient patient = patientRepository.save(new Patient("HF-EVENT-3", "Paciente Evento 3"));
        createEvent(staffToken, patient, "Control medico", PatientEventType.OTHER);

        PatientEventCreateRequest tutorRequest = new PatientEventCreateRequest(
            patient.getPublicId(),
            PatientEventType.VISIT,
            "Intento tutor",
            null,
            Instant.now().plus(1, ChronoUnit.DAYS),
            30,
            null,
            null,
            null
        );
        mockMvc.perform(post("/api/events")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tutorRequest)))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/events/patient/{patientPublicId}", patient.getPublicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].title").value("Control medico"));
    }

    @Test
    void tutorSeesOnlyUpcomingEventsInsideThirtyDayWindow() throws Exception {
        String tutorToken = registerAndLoginTutor("event-tutor-3@example.com");
        String staffToken = createStaffAndLogin("event-staff-4@example.com");
        Patient patient = patientRepository.save(new Patient("HF-EVENT-4", "Paciente Evento 4"));

        createEventAt(staffToken, patient, "Evento pasado", PatientEventType.OTHER, Instant.now().minus(1, ChronoUnit.DAYS));
        createEventAt(staffToken, patient, "Evento visible", PatientEventType.EXAM, Instant.now().plus(7, ChronoUnit.DAYS));
        createEventAt(staffToken, patient, "Evento lejano", PatientEventType.VISIT, Instant.now().plus(31, ChronoUnit.DAYS));

        MvcResult requestResult = requestLink(tutorToken, "HF-EVENT-4");
        Long linkId = objectMapper.readTree(requestResult.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(put("/api/linking/{id}/approve", linkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/patients/{patientPublicId}/events", patient.getPublicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].title").value("Evento visible"));
    }

    private MvcResult createEvent(String staffToken, Patient patient, String title, PatientEventType type) throws Exception {
        return createEventAt(staffToken, patient, title, type, Instant.now().plus(1, ChronoUnit.DAYS));
    }

    private MvcResult createEventAt(
        String staffToken,
        Patient patient,
        String title,
        PatientEventType type,
        Instant scheduledAt
    ) throws Exception {
        PatientEventCreateRequest request = new PatientEventCreateRequest(
            patient.getPublicId(),
            type,
            title,
            "Informacion visible para familia",
            scheduledAt,
            45,
            "Pediatria",
            "Segundo piso",
            "Equipo tratante"
        );
        return mockMvc.perform(post("/api/events")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult requestLink(String tutorToken, String patientCode) throws Exception {
        return mockMvc.perform(post("/api/linking/request")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkRequestCreateRequest(patientCode))))
            .andExpect(status().isCreated())
            .andReturn();
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
