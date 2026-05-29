package com.hospitalfamilia.server.auth.service;

import com.hospitalfamilia.server.auth.dto.LoginRequest;
import com.hospitalfamilia.server.auth.dto.LoginResponse;
import com.hospitalfamilia.server.auth.dto.LogoutRequest;
import com.hospitalfamilia.server.auth.dto.AuthSessionDto;
import com.hospitalfamilia.server.auth.dto.PasswordResetConfirmRequest;
import com.hospitalfamilia.server.auth.dto.PasswordResetRequest;
import com.hospitalfamilia.server.auth.dto.PasswordResetRequestResponse;
import com.hospitalfamilia.server.auth.dto.RegisterRequest;
import com.hospitalfamilia.server.auth.dto.RevokeOtherSessionsRequest;
import com.hospitalfamilia.server.auth.dto.TokenRefreshRequest;
import com.hospitalfamilia.server.auth.dto.UserDto;
import com.hospitalfamilia.server.auth.entity.AuthSession;
import com.hospitalfamilia.server.auth.entity.PasswordResetToken;
import com.hospitalfamilia.server.auth.entity.Role;
import com.hospitalfamilia.server.auth.entity.RoleName;
import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.exception.AuthException;
import com.hospitalfamilia.server.auth.exception.UserAlreadyExistsException;
import com.hospitalfamilia.server.auth.repository.AuthSessionRepository;
import com.hospitalfamilia.server.auth.repository.PasswordResetTokenRepository;
import com.hospitalfamilia.server.auth.repository.RoleRepository;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.auth.security.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final boolean exposePasswordResetToken;
    private final long passwordResetExpirationMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
        UserRepository userRepository,
        AuthSessionRepository authSessionRepository,
        PasswordResetTokenRepository passwordResetTokenRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtTokenProvider jwtTokenProvider,
        @Value("${app.password-reset.expose-token:false}") boolean exposePasswordResetToken,
        @Value("${app.password-reset.expiration-minutes:30}") long passwordResetExpirationMinutes
    ) {
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.exposePasswordResetToken = exposePasswordResetToken;
        this.passwordResetExpirationMinutes = passwordResetExpirationMinutes;
    }

    @Transactional
    public UserDto register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new AuthException("Las contrasenas no coinciden");
        }

        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new UserAlreadyExistsException("Ya existe un usuario con ese email");
        }

        Role tutorRole = roleRepository.findByName(RoleName.TUTOR)
            .orElseThrow(() -> new AuthException("Rol TUTOR no configurado"));

        User user = new User(
            normalizedEmail,
            passwordEncoder.encode(request.password()),
            request.firstName().trim(),
            request.lastName().trim(),
            request.phoneNumber()
        );
        user.addRole(tutorRole);

        return toDto(userRepository.save(user));
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
            User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new AuthException("Usuario no encontrado"));
            List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
            return issueTokens(user, normalizedEmail, roles, authentication);
        } catch (BadCredentialsException ex) {
            throw new AuthException("Credenciales invalidas");
        }
    }

    @Transactional
    public PasswordResetRequestResponse requestPasswordReset(PasswordResetRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        return userRepository.findByEmailIgnoreCase(normalizedEmail)
            .map(user -> {
                String rawToken = generateResetToken();
                PasswordResetToken resetToken = new PasswordResetToken(
                    hashToken(rawToken),
                    user,
                    Instant.now().plusSeconds(passwordResetExpirationMinutes * 60)
                );
                passwordResetTokenRepository.save(resetToken);
                return new PasswordResetRequestResponse(true, exposePasswordResetToken ? rawToken : null);
            })
            .orElseGet(() -> new PasswordResetRequestResponse(true, null));
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new AuthException("Las contrasenas no coinciden");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hashToken(request.token()))
            .orElseThrow(() -> new AuthException("Token de recuperacion invalido o expirado"));
        if (resetToken.isUsed() || resetToken.isExpired()) {
            throw new AuthException("Token de recuperacion invalido o expirado");
        }

        User user = resetToken.getUser();
        user.changePassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        resetToken.markUsed();
        passwordResetTokenRepository.save(resetToken);
        revokeActiveSessions(user);
    }

    @Transactional
    public LoginResponse refresh(TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();
        if (!jwtTokenProvider.isTokenValid(refreshToken) || !"refresh".equals(jwtTokenProvider.getTokenType(refreshToken))) {
            throw new AuthException("Token de refresco invalido o expirado");
        }

        AuthSession authSession = findActiveRefreshSession(refreshToken);
        String email = jwtTokenProvider.getSubject(refreshToken);
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new AuthException("Usuario no encontrado"));
        List<String> roles = user.getRoles().stream()
            .map(role -> "ROLE_" + role.getName().name())
            .toList();
        Authentication authentication = new UsernamePasswordAuthenticationToken(email, null, roles.stream()
            .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
            .toList());
        if (!authSession.getUser().getId().equals(user.getId())) {
            throw new AuthException("La sesion no coincide con el usuario autenticado");
        }
        authSession.revoke();
        authSessionRepository.save(authSession);

        return issueTokens(user, email, roles, authentication);
    }

    @Transactional(readOnly = true)
    public UserDto currentUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .map(this::toDto)
            .orElseThrow(() -> new AuthException("Usuario no encontrado"));
    }

    @Transactional
    public void logout(LogoutRequest request) {
        String refreshToken = request.refreshToken();
        if (!jwtTokenProvider.isTokenValid(refreshToken) || !"refresh".equals(jwtTokenProvider.getTokenType(refreshToken))) {
            throw new AuthException("Token de refresco invalido o expirado");
        }

        AuthSession authSession = findSessionByToken(refreshToken);
        if (!authSession.isRevoked()) {
            authSession.revoke();
            authSessionRepository.save(authSession);
        }
    }

    @Transactional(readOnly = true)
    public List<AuthSessionDto> sessions(String email, String currentRefreshToken) {
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new AuthException("Usuario no encontrado"));
        UUID currentSessionId = extractCurrentSessionId(currentRefreshToken);

        return authSessionRepository.findByUserOrderByCreatedAtDesc(user).stream()
            .map(session -> toSessionDto(session, currentSessionId))
            .toList();
    }

    @Transactional
    public void revokeSession(String email, UUID sessionId) {
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new AuthException("Usuario no encontrado"));
        AuthSession authSession = authSessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new AuthException("Sesion no encontrada"));
        if (!authSession.getUser().getId().equals(user.getId())) {
            throw new AuthException("La sesion no pertenece al usuario autenticado");
        }
        if (!authSession.isRevoked()) {
            authSession.revoke();
            authSessionRepository.save(authSession);
        }
    }

    @Transactional
    public void revokeOtherSessions(String email, RevokeOtherSessionsRequest request) {
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new AuthException("Usuario no encontrado"));
        AuthSession currentSession = findActiveRefreshSession(request.refreshToken());
        if (!currentSession.getUser().getId().equals(user.getId())) {
            throw new AuthException("La sesion no coincide con el usuario autenticado");
        }

        List<AuthSession> sessions = authSessionRepository.findByUserOrderByCreatedAtDesc(user);
        sessions.stream()
            .filter(session -> !session.getSessionId().equals(currentSession.getSessionId()))
            .filter(session -> !session.isRevoked())
            .forEach(AuthSession::revoke);
        authSessionRepository.saveAll(sessions);
    }

    private void revokeActiveSessions(User user) {
        List<AuthSession> sessions = authSessionRepository.findByUserAndRevokedAtIsNull(user);
        sessions.stream()
            .filter(session -> !session.isExpired())
            .forEach(AuthSession::revoke);
        authSessionRepository.saveAll(sessions);
    }

    private AuthSession findActiveRefreshSession(String refreshToken) {
        AuthSession authSession = findSessionByToken(refreshToken);
        if (authSession.isRevoked() || authSession.isExpired()) {
            throw new AuthException("Sesion revocada o expirada");
        }
        return authSession;
    }

    private UUID extractCurrentSessionId(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        if (!jwtTokenProvider.isTokenValid(refreshToken) || !"refresh".equals(jwtTokenProvider.getTokenType(refreshToken))) {
            return null;
        }
        return jwtTokenProvider.getTokenId(refreshToken);
    }

    private AuthSessionDto toSessionDto(AuthSession authSession, UUID currentSessionId) {
        return new AuthSessionDto(
            authSession.getSessionId(),
            authSession.getCreatedAt(),
            authSession.getUpdatedAt(),
            authSession.getExpiresAt(),
            authSession.getRevokedAt(),
            authSession.isRevoked(),
            currentSessionId != null && currentSessionId.equals(authSession.getSessionId())
        );
    }

    private AuthSession findSessionByToken(String refreshToken) {
        UUID sessionId = jwtTokenProvider.getTokenId(refreshToken);
        AuthSession authSession = authSessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new AuthException("Sesion no encontrada"));
        if (!"refresh".equals(authSession.getTokenType())) {
            throw new AuthException("Tipo de sesion invalido");
        }
        if (authSession.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthException("Sesion expirada");
        }
        return authSession;
    }

    private LoginResponse issueTokens(User user, String email, List<String> roles, Authentication authentication) {
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        UUID sessionId = UUID.randomUUID();
        String refreshToken = jwtTokenProvider.generateRefreshToken(email, roles, sessionId);
        authSessionRepository.save(new AuthSession(
            sessionId,
            user,
            "refresh",
            jwtTokenProvider.getExpiration(refreshToken)
        ));

        return new LoginResponse("Bearer", accessToken, refreshToken, jwtTokenProvider.getAccessExpirationMs(), toDto(user));
    }

    private UserDto toDto(User user) {
        Set<String> roles = user.getRoles().stream()
            .map(role -> role.getName().name())
            .collect(Collectors.toUnmodifiableSet());
        return new UserDto(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhoneNumber(),
            roles
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.trim().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new AuthException("No pudimos procesar el token de recuperacion");
        }
    }
}
