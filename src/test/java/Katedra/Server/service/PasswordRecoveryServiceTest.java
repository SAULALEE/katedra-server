package Katedra.Server.service;

import Katedra.Server.model.AuthProvider;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import Katedra.Server.model.PasswordRecoveryToken;
import Katedra.Server.repository.PasswordRecoveryTokenRepository;
import Katedra.Server.dto.GeneratedPasswordRecoveryTokenDTO;
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
import static org.mockito.ArgumentMatchers.eq;
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

        GeneratedPasswordRecoveryTokenDTO generated = service.generateToken(student.getEmail()).orElseThrow();

        LocalDateTime afterGeneration = LocalDateTime.now(ZoneOffset.UTC);
        ArgumentCaptor<PasswordRecoveryToken> tokenCaptor = ArgumentCaptor.forClass(PasswordRecoveryToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordRecoveryToken persisted = tokenCaptor.getValue();

        assertThat(generated.token()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(generated.rol()).isEqualTo(RolUsuario.ROLE_ALUMNO);
        assertThat(persisted.getTokenHash()).isEqualTo(sha256(generated.token()));
        assertThat(persisted.getTokenHash()).isNotEqualTo(generated.token());
        assertThat(persisted.getUsuario()).isSameAs(student);
        assertThat(persisted.getConsumedAt()).isNull();
        assertThat(persisted.getExpiresAt()).isEqualTo(generated.expiresAt());
        assertThat(generated.expiresAt()).isBetween(
                beforeGeneration.plusMinutes(15), afterGeneration.plusMinutes(15));
    }

    @Test
    void shouldGenerateTokenForTeacher() {
        Usuario teacher = new Usuario("teacher@example.com", "password-hash", "Teacher", RolUsuario.ROLE_PROFESOR);
        given(usuarioRepository.findByEmail(teacher.getEmail())).willReturn(Optional.of(teacher));
        given(tokenRepository.save(any(PasswordRecoveryToken.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Optional<GeneratedPasswordRecoveryTokenDTO> generated = service.generateToken(teacher.getEmail());

        assertThat(generated).isPresent();
        assertThat(generated.get().rol()).isEqualTo(RolUsuario.ROLE_PROFESOR);
    }

    @Test
    void shouldNotGenerateTokenForAdmin() {
        Usuario admin = new Usuario("admin@example.com", "password-hash", "Admin", RolUsuario.ROLE_ADMIN);
        given(usuarioRepository.findByEmail(admin.getEmail())).willReturn(Optional.of(admin));

        assertThat(service.generateToken(admin.getEmail())).isEmpty();
        verify(tokenRepository, never()).save(any(PasswordRecoveryToken.class));
    }

    @Test
    void shouldNotGenerateTokenForSocialLoginAccount() {
        Usuario student = new Usuario("social@example.com", null, "Social", RolUsuario.ROLE_ALUMNO);
        student.setAuthProvider(AuthProvider.GOOGLE);
        given(usuarioRepository.findByEmail(student.getEmail())).willReturn(Optional.of(student));

        assertThat(service.generateToken(student.getEmail())).isEmpty();
        verify(tokenRepository, never()).save(any(PasswordRecoveryToken.class));
    }

    @Test
    void shouldNotGenerateTokenForDeletedAccount() {
        Usuario student = new Usuario("deleted@example.com", "password-hash", "Deleted", RolUsuario.ROLE_ALUMNO);
        student.setDeletedAt(LocalDateTime.now());
        given(usuarioRepository.findByEmail(student.getEmail())).willReturn(Optional.of(student));

        assertThat(service.generateToken(student.getEmail())).isEmpty();
        verify(tokenRepository, never()).save(any(PasswordRecoveryToken.class));
    }

    @Test
    void shouldNotGenerateTokenWhenEmailDoesNotExist() {
        given(usuarioRepository.findByEmail("missing@example.com")).willReturn(Optional.empty());

        assertThat(service.generateToken("missing@example.com")).isEmpty();
        verify(tokenRepository, never()).save(any(PasswordRecoveryToken.class));
    }

    @Test
    void shouldStopIssuingTokensAfterThreeRequestsInFifteenMinutes() {
        Usuario student = new Usuario("student@example.com", "password-hash", "Student", RolUsuario.ROLE_ALUMNO);
        given(usuarioRepository.findByEmail(student.getEmail())).willReturn(Optional.of(student));
        given(tokenRepository.countByUsuarioAndCreatedAtAfter(eq(student), any(LocalDateTime.class))).willReturn(3L);

        assertThat(service.generateToken(student.getEmail())).isEmpty();
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
        assertThat(student.getPasswordChangedAt()).isEqualTo(token.getConsumedAt());
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
    void shouldRejectTokenForAdminWithoutChangingPassword() throws Exception {
        Usuario teacher = new Usuario("admin@example.com", "password-hash", "Admin", RolUsuario.ROLE_ADMIN);
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
