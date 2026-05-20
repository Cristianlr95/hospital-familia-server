package com.hospitalfamilia.server.auth.controller;

import com.hospitalfamilia.server.auth.dto.LoginRequest;
import com.hospitalfamilia.server.auth.dto.LoginResponse;
import com.hospitalfamilia.server.auth.dto.LogoutRequest;
import com.hospitalfamilia.server.auth.dto.AuthSessionDto;
import com.hospitalfamilia.server.auth.dto.RegisterRequest;
import com.hospitalfamilia.server.auth.dto.RevokeOtherSessionsRequest;
import com.hospitalfamilia.server.auth.dto.TokenRefreshRequest;
import com.hospitalfamilia.server.auth.dto.UserDto;
import com.hospitalfamilia.server.auth.service.AuthService;
import com.hospitalfamilia.server.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserDto>> register(@Valid @RequestBody RegisterRequest request) {
        UserDto user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Usuario tutor registrado", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login exitoso", authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Token refrescado", authService.refresh(request)));
    }

    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<UserDto>> validate(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Token valido", authService.currentUser(principal.getName())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Sesion cerrada correctamente", "OK"));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<AuthSessionDto>>> sessions(
        Principal principal,
        @RequestHeader(value = "X-Refresh-Token", required = false) String currentRefreshToken
    ) {
        return ResponseEntity.ok(ApiResponse.success("Sesiones cargadas", authService.sessions(principal.getName(), currentRefreshToken)));
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    public ResponseEntity<ApiResponse<String>> revokeSession(Principal principal, @PathVariable UUID sessionId) {
        authService.revokeSession(principal.getName(), sessionId);
        return ResponseEntity.ok(ApiResponse.success("Sesion revocada", "OK"));
    }

    @PostMapping("/sessions/revoke-others")
    public ResponseEntity<ApiResponse<String>> revokeOtherSessions(
        Principal principal,
        @Valid @RequestBody RevokeOtherSessionsRequest request
    ) {
        authService.revokeOtherSessions(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Otras sesiones revocadas", "OK"));
    }
}
