package Katedra.Server.student.service;

import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import Katedra.Server.student.dto.GeneratedPasswordRecoveryTokenDTO;
import Katedra.Server.student.model.PasswordRecoveryToken;
import Katedra.Server.student.repository.PasswordRecoveryTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordRecoveryService {

    private static final int TOKEN_SIZE_BYTES = 32;
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    private final UsuarioRepository usuarioRepository;
    private final PasswordRecoveryTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordRecoveryService(UsuarioRepository usuarioRepository,
                                   PasswordRecoveryTokenRepository tokenRepository,
                                   PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public GeneratedPasswordRecoveryTokenDTO generateTokenForStudent(String email) {
        Usuario student = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado"));

        if (student.getRol() != RolUsuario.ROLE_ALUMNO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Recuperación disponible solo para alumnos");
        }

        byte[] randomBytes = new byte[TOKEN_SIZE_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plus(TOKEN_TTL);

        tokenRepository.save(new PasswordRecoveryToken(student, hashToken(rawToken), expiresAt));
        return new GeneratedPasswordRecoveryTokenDTO(rawToken, expiresAt);
    }

    /** Resets a student's password and consumes the recovery token in one transaction. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 8 || newPassword.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener entre 8 y 100 caracteres");
        }

        if (rawToken == null || rawToken.isBlank()) {
            throw invalidRecoveryToken();
        }

        String tokenHash = hashToken(rawToken);
        PasswordRecoveryToken token = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidRecoveryToken);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Usuario student = token.getUsuario();

        if (token.getConsumedAt() != null
                || !token.getExpiresAt().isAfter(now)
                || student.getRol() != RolUsuario.ROLE_ALUMNO) {
            throw invalidRecoveryToken();
        }

        String encodedPassword = passwordEncoder.encode(newPassword);
        student.setPassword(encodedPassword);
        student.setUpdatedAt(now);
        usuarioRepository.save(student);

        token.setConsumedAt(now);
        tokenRepository.save(token);
    }

    private ResponseStatusException invalidRecoveryToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token de recuperación inválido o expirado");
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }
}
