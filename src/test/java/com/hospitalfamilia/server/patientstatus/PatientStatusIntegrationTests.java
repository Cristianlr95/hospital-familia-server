package com.hospitalfamilia.server.patientstatus;

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
import com.hospitalfamilia.server.patientstatus.entity.PatientCareSnapshot;
import com.hospitalfamilia.server.patientstatus.dto.PatientStatusUpdateRequest;
import com.hospitalfamilia.server.patientstatus.repository.PatientCareSnapshotRepository;
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
class PatientStatusIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PatientCareSnapshotRepository snapshotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void tutorSeesLimitedPatientStatusOnlyAfterApproval() throws Exception {
        String tutorToken = registerAndLoginTutor("status-tutor-1@example.com");
        String staffToken = createStaffAndLogin("status-staff-1@example.com");
        Patient patient = patientRepository.save(new Patient("HF-STATUS-1", "Paciente Estado 1"));
        snapshotRepository.save(new PatientCareSnapshot(
            patient,
            "Estable",
            "Pediatria",
            "Habitacion 402",
            "Reposo y observacion por equipo tratante"
        ));

        MvcResult requestResult = requestLink(tutorToken, "HF-STATUS-1");
        Long linkId = objectMapper.readTree(requestResult.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(get("/api/patients/my-statuses")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(put("/api/linking/{id}/approve", linkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/patients/my-statuses")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].displayName").value("Paciente Estado 1"))
            .andExpect(jsonPath("$.data[0].careStatus").value("Estable"))
            .andExpect(jsonPath("$.data[0].currentService").value("Pediatria"))
            .andExpect(jsonPath("$.data[0].currentLocation").value("Habitacion 402"))
            .andExpect(jsonPath("$.data[0].summary").value("Reposo y observacion por equipo tratante"))
            .andExpect(jsonPath("$.data[0].patientPublicId").exists());

        mockMvc.perform(get("/api/patients/{patientPublicId}/status", patient.getPublicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.careStatus").value("Estable"));
    }

    @Test
    void tutorCannotReadStatusForUnapprovedOrForeignPatient() throws Exception {
        String firstTutorToken = registerAndLoginTutor("status-tutor-2@example.com");
        String secondTutorToken = registerAndLoginTutor("status-tutor-3@example.com");
        String staffToken = createStaffAndLogin("status-staff-2@example.com");
        Patient patient = patientRepository.save(new Patient("HF-STATUS-2", "Paciente Estado 2"));
        snapshotRepository.save(new PatientCareSnapshot(patient, "En observacion", "Urgencia", "Box 8", null));

        MvcResult requestResult = requestLink(firstTutorToken, "HF-STATUS-2");
        Long linkId = objectMapper.readTree(requestResult.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(get("/api/patients/{patientPublicId}/status", patient.getPublicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(firstTutorToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(put("/api/linking/{id}/approve", linkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/patients/{patientPublicId}/status", patient.getPublicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(secondTutorToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void staffCannotUseTutorPatientStatusEndpoint() throws Exception {
        String staffToken = createStaffAndLogin("status-staff-3@example.com");

        mockMvc.perform(get("/api/patients/my-statuses")
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void staffCanUpdateVisiblePatientStatusForTutors() throws Exception {
        String tutorToken = registerAndLoginTutor("status-tutor-4@example.com");
        String staffToken = createStaffAndLogin("status-staff-4@example.com");
        Patient patient = patientRepository.save(new Patient("HF-STATUS-4", "Paciente Estado 4"));

        MvcResult requestResult = requestLink(tutorToken, "HF-STATUS-4");
        Long linkId = objectMapper.readTree(requestResult.getResponse().getContentAsString()).at("/data/id").asLong();
        mockMvc.perform(put("/api/linking/{id}/approve", linkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk());

        PatientStatusUpdateRequest request = new PatientStatusUpdateRequest(
            "Bajo vigilancia",
            "Medicina interna",
            "Habitacion 512",
            "Control frecuente por equipo de enfermeria"
        );

        mockMvc.perform(put("/api/patients/{patientPublicId}/status", patient.getPublicId())
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.careStatus").value("Bajo vigilancia"))
            .andExpect(jsonPath("$.data.currentService").value("Medicina interna"))
            .andExpect(jsonPath("$.data.currentLocation").value("Habitacion 512"))
            .andExpect(jsonPath("$.data.summary").value("Control frecuente por equipo de enfermeria"));

        mockMvc.perform(get("/api/patients/{patientPublicId}/status", patient.getPublicId())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.careStatus").value("Bajo vigilancia"))
            .andExpect(jsonPath("$.data.currentLocation").value("Habitacion 512"));
    }

    @Test
    void tutorCannotUpdatePatientStatus() throws Exception {
        String tutorToken = registerAndLoginTutor("status-tutor-5@example.com");
        Patient patient = patientRepository.save(new Patient("HF-STATUS-5", "Paciente Estado 5"));

        PatientStatusUpdateRequest request = new PatientStatusUpdateRequest("Estable", null, null, null);

        mockMvc.perform(put("/api/patients/{patientPublicId}/status", patient.getPublicId())
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
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
