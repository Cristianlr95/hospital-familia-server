package com.hospitalfamilia.server.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
        @Value("${app.jwt.secret}") String jwtSecret,
        @Value("${app.jwt.expiration-ms}") long accessExpirationMs,
        @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList();
        return generateToken(authentication.getName(), roles, accessExpirationMs, "access");
    }

    public String generateRefreshToken(String email, Collection<String> roles, UUID sessionId) {
        return generateToken(email, roles, refreshExpirationMs, "refresh", sessionId);
    }

    public boolean isTokenValid(String token) {
        try {
            claims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public String getSubject(String token) {
        return claims(token).getSubject();
    }

    public String getTokenType(String token) {
        return claims(token).get("type", String.class);
    }

    public UUID getTokenId(String token) {
        String tokenId = claims(token).getId();
        if (tokenId == null || tokenId.isBlank()) {
            throw new JwtException("Token sin identificador");
        }
        return UUID.fromString(tokenId);
    }

    public Instant getExpiration(String token) {
        return claims(token).getExpiration().toInstant();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        Object roles = claims(token).get("roles");
        if (roles instanceof List<?> roleList) {
            return roleList.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    private String generateToken(String subject, Collection<String> roles, long expirationMs, String type, UUID tokenId) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(subject)
            .id(tokenId == null ? null : tokenId.toString())
            .claim("roles", roles)
            .claim("type", type)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(secretKey)
            .compact();
    }

    private String generateToken(String subject, Collection<String> roles, long expirationMs, String type) {
        return generateToken(subject, roles, expirationMs, type, null);
    }

    private Claims claims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
