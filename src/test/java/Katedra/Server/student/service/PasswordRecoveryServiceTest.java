package Katedra.Server.student.service;

import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import Katedra.Server.student.model.PasswordRecoveryToken;
import Katedra.Server.student.repository.PasswordRecoveryTokenRepository;
import Katedra.Server.student.dto.GeneratedPasswordRecoveryTokenDTO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordRecoveryTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new PasswordRecoveryService(usuarioRepository, tokenRepository, passwordEncoder);
    }

    @Test
    void shouldGenerateSecureTokenAndPersistOnlyItsHashForStudent() throws Exception {
        Usuario student = new Usuario("student@example.com", "password-hash", "Student", RolUsuario.ROLE_ALUMNO);
        given(usuarioRepository.findByEmail(student.getEmail())).willReturn(Optional.of(student));
        given(tokenRepository.save(any(PasswordRecoveryToken.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime beforeGeneration = LocalDateTime.now(ZoneOffset.UTC);

        GeneratedPasswordRecoveryTokenDTO generated = service.generateTokenForStudent(student.getEmail());

        LocalDateTime afterGeneration = LocalDateTime.now(ZoneOffset.UTC);
        ArgumentCaptor<PasswordRecoveryToken> tokenCaptor = ArgumentCaptor.forClass(PasswordRecoveryToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordRecoveryToken persisted = tokenCaptor.getValue();

        assertThat(generated.token()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(persisted.getTokenHash()).isEqualTo(sha256(generated.token()));
        assertThat(persisted.getTokenHash()).isNotEqualTo(generated.token());
        assertThat(persisted.getUsuario()).isSameAs(student);
        assertThat(persisted.getConsumedAt()).isNull();
        assertThat(persisted.getExpiresAt()).isEqualTo(generated.expiresAt());
        assertThat(generated.expiresAt()).isBetween(
                beforeGeneration.plusMinutes(15), afterGeneration.plusMinutes(15));
    }

    @Test
    void shouldRejectRecoveryTokenGenerationForNonStudentUsers() {
        Usuario teacher = new Usuario("teacher@example.com", "password-hash", "Teacher", RolUsuario.ROLE_PROFESOR);
        given(usuarioRepository.findByEmail(teacher.getEmail())).willReturn(Optional.of(teacher));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.generateTokenForStudent(teacher.getEmail()));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(tokenRepository, never()).save(any(PasswordRecoveryToken.class));
    }

    @Test
    void shouldRejectRecoveryTokenGenerationWhenStudentDoesNotExist() {
        given(usuarioRepository.findByEmail("missing@example.com")).willReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.generateTokenForStudent("missing@example.com"));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(tokenRepository, never()).save(any(PasswordRecoveryToken.class));
    }

    @Test
    void shouldResetStudentPasswordAndConsumeTokenOnlyOnce() throws Exception {
        Usuario student = new Usuario("student@example.com", "password-hash", "Student", RolUsuario.ROLE_ALUMNO);
        org.springframework.test.util.ReflectionTestUtils.setField(student, "id", "student-id");
        PasswordEncoder bcryptEncoder = new BCryptPasswordEncoder();
        service = new PasswordRecoveryService(usuarioRepository, tokenRepository, bcryptEncoder);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        PasswordRecoveryToken token = new PasswordRecoveryToken(student, sha256("raw-recovery-token"), now.plusMinutes(15));
        given(tokenRepository.findByTokenHashForUpdate(sha256("raw-recovery-token")))
                .willReturn(Optional.of(token));
        given(usuarioRepository.save(student)).willReturn(student);
        given(tokenRepository.save(token)).willReturn(token);

        service.resetPassword("raw-recovery-token", "newPassword123");
        ResponseStatusException secondUse = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword("raw-recovery-token", "newPassword123"));

        assertThat(secondUse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(secondUse.getReason()).isEqualTo("Token de recuperación inválido o expirado");
        assertThat(student.getPassword()).startsWith("$2");
        assertThat(bcryptEncoder.matches("newPassword123", student.getPassword())).isTrue();
        assertThat(token.getConsumedAt()).isNotNull();
        verify(usuarioRepository).save(student);
        verify(tokenRepository).save(token);
    }

    @Test
    void shouldRejectExpiredTokenWithoutChangingPasswordOrConsumingIt() throws Exception {
        Usuario student = new Usuario("student@example.com", "password-hash", "Student", RolUsuario.ROLE_ALUMNO);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        PasswordRecoveryToken token = new PasswordRecoveryToken(student, sha256("expired-token"), now.minusSeconds(1));
        given(tokenRepository.findByTokenHashForUpdate(sha256("expired-token")))
                .willReturn(Optional.of(token));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword("expired-token", "newPassword123"));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("Token de recuperación inválido o expirado");
        assertThat(token.getConsumedAt()).isNull();
        assertThat(student.getPassword()).isEqualTo("password-hash");
        verify(passwordEncoder, never()).encode(any());
        verify(usuarioRepository, never()).save(any(Usuario.class));
        verify(tokenRepository, never()).save(any(PasswordRecoveryToken.class));
    }

    @Test
    void shouldRejectTokenForNonStudentWithoutChangingPassword() throws Exception {
        Usuario teacher = new Usuario("teacher@example.com", "password-hash", "Teacher", RolUsuario.ROLE_PROFESOR);
        PasswordRecoveryToken token = new PasswordRecoveryToken(
                teacher, sha256("teacher-token"), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15));
        given(tokenRepository.findByTokenHashForUpdate(sha256("teacher-token")))
                .willReturn(Optional.of(token));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword("teacher-token", "newPassword123"));

        assertThat(exception.getReason()).isEqualTo("Token de recuperación inválido o expirado");
        assertThat(teacher.getPassword()).isEqualTo("password-hash");
        assertThat(token.getConsumedAt()).isNull();
        verify(passwordEncoder, never()).encode(any());
        verify(usuarioRepository, never()).save(any(Usuario.class));
        verify(tokenRepository, never()).save(any(PasswordRecoveryToken.class));
    }

    @Test
    void shouldNotConsumeTokenWhenPasswordPersistenceFails() throws Exception {
        Usuario student = new Usuario("student@example.com", "password-hash", "Student", RolUsuario.ROLE_ALUMNO);
        PasswordRecoveryToken token = new PasswordRecoveryToken(
                student, sha256("valid-token"), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15));
        given(tokenRepository.findByTokenHashForUpdate(sha256("valid-token")))
                .willReturn(Optional.of(token));
        given(passwordEncoder.encode("newPassword123")).willReturn("bcrypt-hash");
        given(usuarioRepository.save(student)).willThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class,
                () -> service.resetPassword("valid-token", "newPassword123"));

        assertThat(token.getConsumedAt()).isNull();
        verify(tokenRepository, never()).save(any(PasswordRecoveryToken.class));
    }

    private String sha256(String token) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
