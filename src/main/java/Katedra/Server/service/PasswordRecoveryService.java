package Katedra.Server.service;

import Katedra.Server.model.AuthProvider;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import Katedra.Server.dto.GeneratedPasswordRecoveryTokenDTO;
import Katedra.Server.model.PasswordRecoveryToken;
import Katedra.Server.repository.PasswordRecoveryTokenRepository;
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
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

@Service
public class PasswordRecoveryService {

    private static final int TOKEN_SIZE_BYTES = 32;
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);
    private static final long MAX_REQUESTS_PER_WINDOW = 3;
    private static final Set<RolUsuario> RECOVERABLE_ROLES = EnumSet.of(RolUsuario.ROLE_PROFESOR, RolUsuario.ROLE_ALUMNO);

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

    /**
     * Issues a recovery token for an eligible account. Returns empty, without revealing why,
     * when the email is unknown, the account is deleted, uses social login, is an admin,
     * or has reached the request limit.
     */
    @Transactional
    public Optional<GeneratedPasswordRecoveryTokenDTO> generateToken(String email) {
        Optional<Usuario> found = usuarioRepository.findByEmail(email);
        if (found.isEmpty() || !isEligible(found.get())) {
            return Optional.empty();
        }
        Usuario usuario = found.get();

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (tokenRepository.countByUsuarioAndCreatedAtAfter(usuario, now.minus(RATE_LIMIT_WINDOW))
                >= MAX_REQUESTS_PER_WINDOW) {
            return Optional.empty();
        }

        byte[] randomBytes = new byte[TOKEN_SIZE_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        LocalDateTime expiresAt = now.plus(TOKEN_TTL);

        tokenRepository.save(new PasswordRecoveryToken(usuario, hashToken(rawToken), expiresAt));
        return Optional.of(new GeneratedPasswordRecoveryTokenDTO(rawToken, expiresAt, usuario.getRol()));
    }

    private boolean isEligible(Usuario usuario) {
        return usuario.getDeletedAt() == null
                && usuario.getAuthProvider() == AuthProvider.LOCAL
                && RECOVERABLE_ROLES.contains(usuario.getRol());
    }

    /**
     * Resets the password, consumes the recovery token and stamps passwordChangedAt so
     * every JWT issued before the reset is rejected, all in one transaction.
     */
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
                || !isEligible(student)) {
            throw invalidRecoveryToken();
        }

        String encodedPassword = passwordEncoder.encode(newPassword);
        student.setPassword(encodedPassword);
        student.setUpdatedAt(now);
        student.setPasswordChangedAt(now);
        student.setMustChangePassword(false);
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
