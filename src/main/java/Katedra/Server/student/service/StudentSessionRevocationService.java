package Katedra.Server.student.service;

import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import Katedra.Server.service.JwtService;
import Katedra.Server.student.repository.StudentSessionRevocationRepository;
import io.jsonwebtoken.JwtException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Stores only student-session fingerprints, never the bearer JWT. */
@Service
public class StudentSessionRevocationService {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final StudentSessionRevocationRepository revocationRepository;

    public StudentSessionRevocationService(
            JwtService jwtService,
            UsuarioRepository usuarioRepository,
            StudentSessionRevocationRepository revocationRepository
    ) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.revocationRepository = revocationRepository;
    }

    /** Invalid or non-student tokens are ignored so the legacy public logout stays compatible. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        try {
            String email = jwtService.extractUsername(rawToken);
            if (!RolUsuario.ROLE_ALUMNO.name().equals(jwtService.extractRole(rawToken))) {
                return;
            }
            Optional<Usuario> candidate = usuarioRepository.findByEmail(email);
            if (candidate.isEmpty()) {
                return;
            }

            Usuario student = candidate.get();
            if (student.getRol() != RolUsuario.ROLE_ALUMNO || !jwtService.isTokenValid(rawToken, student)) {
                return;
            }

            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            Date expirationDate = jwtService.extractExpirationDate(rawToken);
            LocalDateTime expiresAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(expirationDate.getTime()),
                    ZoneOffset.UTC);
            if (!expiresAt.isAfter(now)) {
                return;
            }

            String sessionKeyHash = hashSessionIdentifier(rawToken);
            revocationRepository.insertIfAbsent(
                    UUID.randomUUID().toString(), student.getId(), sessionKeyHash, expiresAt, now);
        } catch (JwtException | IllegalArgumentException ignored) {
            // Do not disclose token parse details from the public, idempotent logout endpoint.
        }
    }

    /** Called only after signature, expiry, and database role have been validated by the JWT filter. */
    @Transactional(readOnly = true)
    public boolean isRevoked(String rawToken) {
        return revocationRepository.existsBySessionKeyHashAndExpiresAtAfter(
                hashSessionIdentifier(rawToken), LocalDateTime.now(ZoneOffset.UTC));
    }

    /** Expired JWTs are rejected independently; remove their revocation markers once daily. */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void cleanupExpiredRevocations() {
        revocationRepository.deleteExpired(LocalDateTime.now(ZoneOffset.UTC));
    }

    private String hashSessionIdentifier(String rawToken) {
        String tokenId = jwtService.extractTokenId(rawToken);
        String identifier = tokenId == null || tokenId.isBlank()
                ? "legacy-jwt:" + rawToken
                : "jti:" + tokenId;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(identifier.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
