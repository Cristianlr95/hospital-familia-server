package com.hospitalfamilia.server.auth.service;

import com.hospitalfamilia.server.auth.dto.LoginRequest;
import com.hospitalfamilia.server.auth.dto.LoginResponse;
import com.hospitalfamilia.server.auth.dto.LogoutRequest;
import com.hospitalfamilia.server.auth.dto.RegisterRequest;
import com.hospitalfamilia.server.auth.dto.TokenRefreshRequest;
import com.hospitalfamilia.server.auth.dto.UserDto;
import com.hospitalfamilia.server.auth.entity.AuthSession;
import com.hospitalfamilia.server.auth.entity.Role;
import com.hospitalfamilia.server.auth.entity.RoleName;
import com.hospitalfamilia.server.auth.entity.User;
import com.hospitalfamilia.server.auth.exception.AuthException;
import com.hospitalfamilia.server.auth.exception.UserAlreadyExistsException;
import com.hospitalfamilia.server.auth.repository.AuthSessionRepository;
import com.hospitalfamilia.server.auth.repository.RoleRepository;
import com.hospitalfamilia.server.auth.repository.UserRepository;
import com.hospitalfamilia.server.auth.security.JwtTokenProvider;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
        UserRepository userRepository,
        AuthSessionRepository authSessionRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
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

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
            User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new AuthException("Usuario no encontrado"));
            String accessToken = jwtTokenProvider.generateAccessToken(authentication);
            List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
            UUID sessionId = UUID.randomUUID();
            String refreshToken = jwtTokenProvider.generateRefreshToken(normalizedEmail, roles, sessionId);
            authSessionRepository.save(new AuthSession(
                sessionId,
                user,
                "refresh",
                jwtTokenProvider.getExpiration(refreshToken)
            ));

            return new LoginResponse("Bearer", accessToken, refreshToken, jwtTokenProvider.getAccessExpirationMs(), toDto(user));
        } catch (BadCredentialsException ex) {
            throw new AuthException("Credenciales invalidas");
        }
    }

    @Transactional(readOnly = true)
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
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        if (!authSession.getUser().getId().equals(user.getId())) {
            throw new AuthException("La sesion no coincide con el usuario autenticado");
        }

        return new LoginResponse("Bearer", accessToken, refreshToken, jwtTokenProvider.getAccessExpirationMs(), toDto(user));
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

    private AuthSession findActiveRefreshSession(String refreshToken) {
        AuthSession authSession = findSessionByToken(refreshToken);
        if (authSession.isRevoked() || authSession.isExpired()) {
            throw new AuthException("Sesion revocada o expirada");
        }
        return authSession;
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
}
