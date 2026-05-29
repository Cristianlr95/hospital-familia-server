package com.hospitalfamilia.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospitalfamilia.server.auth.dto.LoginRequest;
import com.hospitalfamilia.server.auth.dto.LogoutRequest;
import com.hospitalfamilia.server.auth.dto.PasswordResetConfirmRequest;
import com.hospitalfamilia.server.auth.dto.PasswordResetRequest;
import com.hospitalfamilia.server.auth.dto.RegisterRequest;
import com.hospitalfamilia.server.auth.dto.TokenRefreshRequest;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.auth.security.JwtTokenProvider;
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
class AuthFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void registerCreatesTutorWithHashedPassword() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "tutora@example.com",
            "password123",
            "password123",
            "Maria",
            "Lagos",
            "+56911111111"
        );

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value("tutora@example.com"))
            .andExpect(jsonPath("$.data.roles[0]").value("TUTOR"))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        var savedUser = userRepository.findByEmailIgnoreCase("tutora@example.com").orElseThrow();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", savedUser.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateRegisterReturnsConflict() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "duplicada@example.com",
            "password123",
            "password123",
            "Ana",
            "Perez",
            null
        );

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginRefreshValidateAndLogoutWorkWithJwt() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
            "flujo@example.com",
            "password123",
            "password123",
            "Cristian",
            "Lagos",
            "+56922222222"
        );
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("flujo@example.com", "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.at("/data/accessToken").asText();
        String refreshToken = loginJson.at("/data/refreshToken").asText();

        assertThat(jwtTokenProvider.getSubject(accessToken)).isEqualTo("flujo@example.com");
        assertThat(jwtTokenProvider.getRoles(accessToken)).contains("ROLE_TUTOR");

        mockMvc.perform(get("/api/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value("flujo@example.com"));

        TokenRefreshRequest refreshRequest = new TokenRefreshRequest(refreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andReturn();

        JsonNode refreshJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String rotatedRefreshToken = refreshJson.at("/data/refreshToken").asText();
        assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/auth/logout")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LogoutRequest(rotatedRefreshToken))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("OK"));

        mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TokenRefreshRequest(rotatedRefreshToken))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void sessionListingAndRevocationEndpointsWork() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
            "sesiones@example.com",
            "password123",
            "password123",
            "Sesion",
            "Prueba",
            null
        );
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

        JsonNode firstLogin = login("sesiones@example.com", "password123");
        JsonNode secondLogin = login("sesiones@example.com", "password123");

        String firstAccessToken = firstLogin.at("/data/accessToken").asText();
        String firstRefreshToken = firstLogin.at("/data/refreshToken").asText();
        String secondRefreshToken = secondLogin.at("/data/refreshToken").asText();
        String secondSessionId = jwtTokenProvider.getTokenId(secondRefreshToken).toString();

        MvcResult sessionsResult = mockMvc.perform(get("/api/auth/sessions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccessToken)
                .header("X-Refresh-Token", firstRefreshToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].sessionId").exists())
            .andReturn();

        JsonNode sessionsJson = objectMapper.readTree(sessionsResult.getResponse().getContentAsString()).at("/data");
        int currentCount = 0;
        for (JsonNode session : sessionsJson) {
            if (session.path("current").asBoolean()) {
                currentCount++;
            }
        }
        assertThat(currentCount).isEqualTo(1);

        mockMvc.perform(post("/api/auth/sessions/{sessionId}/revoke", secondSessionId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("OK"));

        mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TokenRefreshRequest(secondRefreshToken))))
            .andExpect(status().isUnauthorized());

        JsonNode thirdLogin = login("sesiones@example.com", "password123");
        String thirdRefreshToken = thirdLogin.at("/data/refreshToken").asText();

        mockMvc.perform(post("/api/auth/sessions/revoke-others")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new com.hospitalfamilia.server.auth.dto.RevokeOtherSessionsRequest(firstRefreshToken))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("OK"));

        mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TokenRefreshRequest(firstRefreshToken))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TokenRefreshRequest(thirdRefreshToken))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointReturnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/validate"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginWithInvalidPasswordReturnsUnauthorized() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
            "fallo@example.com",
            "password123",
            "password123",
            "Pedro",
            "Rojas",
            null
        );
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("fallo@example.com", "wrong-password");
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void passwordResetUpdatesPasswordAndRevokesActiveSessions() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
            "reset@example.com",
            "password123",
            "password123",
            "Reset",
            "Prueba",
            null
        );
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

        JsonNode login = login("reset@example.com", "password123");
        String oldRefreshToken = login.at("/data/refreshToken").asText();

        MvcResult resetResult = mockMvc.perform(post("/api/auth/password-reset/request")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PasswordResetRequest("reset@example.com"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accepted").value(true))
            .andExpect(jsonPath("$.data.devResetToken").isNotEmpty())
            .andReturn();
        String resetToken = objectMapper.readTree(resetResult.getResponse().getContentAsString())
            .at("/data/devResetToken")
            .asText();

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PasswordResetConfirmRequest(resetToken, "newpassword123", "newpassword123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("OK"));

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("reset@example.com", "password123"))))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("reset@example.com", "newpassword123"))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TokenRefreshRequest(oldRefreshToken))))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PasswordResetConfirmRequest(resetToken, "anotherpass123", "anotherpass123"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetRequestDoesNotRevealUnknownEmails() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PasswordResetRequest("nadie@example.com"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accepted").value(true))
            .andExpect(jsonPath("$.data.devResetToken").doesNotExist());
    }

    private JsonNode login(String email, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString());
    }
}
