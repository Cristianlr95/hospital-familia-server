package com.hospitalfamilia.server.contact;

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
import com.hospitalfamilia.server.contact.dto.ContactRequestCreateRequest;
import com.hospitalfamilia.server.contact.dto.ContactRequestResolveRequest;
import com.hospitalfamilia.server.linking.dto.LinkRequestCreateRequest;
import com.hospitalfamilia.server.patients.dto.StaffPatientCreateRequest;
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
class ContactRequestIntegrationTests {

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
    void tutorCanCreateContactRequestAndStaffCanResolveIt() throws Exception {
        String staffToken = createStaffAndLogin("contact-staff@example.com");
        String tutorToken = registerAndLoginTutor("contact-tutor@example.com");
        UUID patientPublicId = createPatient(staffToken, "Paciente Contacto", "HF-CONTACT-001");
        approveLink(staffToken, tutorToken, "HF-CONTACT-001");

        ContactRequestCreateRequest request = new ContactRequestCreateRequest(
            patientPublicId,
            "Necesito orientacion sobre el horario de visita familiar."
        );
        MvcResult createResult = mockMvc.perform(post("/api/contact-requests")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andExpect(jsonPath("$.data.patientDisplayName").value("Paciente Contacto"))
            .andReturn();
        long contactRequestId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .at("/data/id")
            .asLong();

        mockMvc.perform(get("/api/contact-requests/staff/open")
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].id", hasItem((int) contactRequestId)));

        ContactRequestResolveRequest resolveRequest = new ContactRequestResolveRequest(
            "Staff reviso la solicitud y contactara por telefono."
        );
        mockMvc.perform(put("/api/contact-requests/staff/{id}/resolve", contactRequestId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resolveRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("RESOLVED"))
            .andExpect(jsonPath("$.data.resolutionNote").value("Staff reviso la solicitud y contactara por telefono."));

        mockMvc.perform(get("/api/notifications")
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].type", hasItem("CONTACT_REQUEST_RESOLVED")));
    }

    @Test
    void tutorCannotCreateContactRequestForUnapprovedPatient() throws Exception {
        String staffToken = createStaffAndLogin("contact-staff-blocked@example.com");
        String tutorToken = registerAndLoginTutor("contact-tutor-blocked@example.com");
        UUID patientPublicId = createPatient(staffToken, "Paciente Bloqueado Contacto", "HF-CONTACT-002");

        ContactRequestCreateRequest request = new ContactRequestCreateRequest(patientPublicId, "Solicito contacto.");
        mockMvc.perform(post("/api/contact-requests")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Paciente no vinculado o no aprobado para contacto"));
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

    private void approveLink(String staffToken, String tutorToken, String patientCode) throws Exception {
        MvcResult linkResult = mockMvc.perform(post("/api/linking/request")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkRequestCreateRequest(patientCode))))
            .andExpect(status().isCreated())
            .andReturn();
        long linkId = objectMapper.readTree(linkResult.getResponse().getContentAsString())
            .at("/data/id")
            .asLong();

        mockMvc.perform(put("/api/linking/{id}/approve", linkId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
            .andExpect(status().isOk());
    }

    private String registerAndLoginTutor(String email) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(email, "password123", "password123", "Tutor", "Contacto", null);
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());
        return login(email, "password123");
    }

    private String createStaffAndLogin(String email) throws Exception {
        var staffRole = roleRepository.findByName(RoleName.STAFF).orElseThrow();
        User staff = new User(email, passwordEncoder.encode("password123"), "Staff", "Contacto", null);
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
