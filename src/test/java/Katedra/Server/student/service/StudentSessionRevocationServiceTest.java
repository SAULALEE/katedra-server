package Katedra.Server.student.service;

import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import Katedra.Server.service.JwtService;
import Katedra.Server.student.repository.StudentSessionRevocationRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StudentSessionRevocationServiceTest {

    private static final String SECRET = "dummysecretforlocaltestingonlypleasedonotuse1234";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private StudentSessionRevocationRepository revocationRepository;

    private JwtService jwtService;
    private StudentSessionRevocationService service;
    private Usuario student;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 900_000L);
        service = new StudentSessionRevocationService(jwtService, usuarioRepository, revocationRepository);
        student = new Usuario("student@example.com", "hash", "Student", RolUsuario.ROLE_ALUMNO);
        ReflectionTestUtils.setField(student, "id", "student-id");
        lenient().when(usuarioRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        lenient().when(revocationRepository.deleteExpired(any(LocalDateTime.class))).thenReturn(0);
    }

    @Test
    void shouldRevokeOnlyTheSpecifiedSessionForAStudent() {
        Set<String> revokedHashes = ConcurrentHashMap.newKeySet();
        given(revocationRepository.insertIfAbsent(anyString(), anyString(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .willAnswer(invocation -> revokedHashes.add(invocation.getArgument(2)) ? 1 : 0);
        given(revocationRepository.existsBySessionKeyHashAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .willAnswer(invocation -> revokedHashes.contains(invocation.getArgument(0)));
        String first = jwtService.generateToken(student);
        String second = jwtService.generateToken(student);

        service.revoke(first);

        assertThat(service.isRevoked(first)).isTrue();
        assertThat(service.isRevoked(second)).isFalse();
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(revocationRepository).insertIfAbsent(anyString(), org.mockito.ArgumentMatchers.eq("student-id"),
                hashCaptor.capture(), any(LocalDateTime.class), any(LocalDateTime.class));
        assertThat(hashCaptor.getValue()).matches("[0-9a-f]{64}").isNotEqualTo(first);
    }

    @Test
    void shouldKeepLogoutIdempotentForTheSameSession() {
        Set<String> revokedHashes = ConcurrentHashMap.newKeySet();
        given(revocationRepository.insertIfAbsent(anyString(), anyString(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .willAnswer(invocation -> revokedHashes.add(invocation.getArgument(2)) ? 1 : 0);
        String token = jwtService.generateToken(student);

        service.revoke(token);
        service.revoke(token);

        assertThat(revokedHashes).hasSize(1);
    }

    @Test
    void shouldRevokeLegacyStudentTokenWithoutJtiUsingItsFingerprint() {
        Set<String> revokedHashes = ConcurrentHashMap.newKeySet();
        given(revocationRepository.insertIfAbsent(anyString(), anyString(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .willAnswer(invocation -> revokedHashes.add(invocation.getArgument(2)) ? 1 : 0);
        given(revocationRepository.existsBySessionKeyHashAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .willAnswer(invocation -> revokedHashes.contains(invocation.getArgument(0)));
        String legacyToken = legacyToken(student.getEmail(), System.currentTimeMillis() + 900_000L);

        service.revoke(legacyToken);

        assertThat(service.isRevoked(legacyToken)).isTrue();
    }

    @Test
    void shouldIgnoreExpiredInvalidAndNonStudentTokens() {
        String expired = legacyToken(student.getEmail(), System.currentTimeMillis() - 1_000L);
        String invalidSignature = legacyTokenWithOtherKey(student.getEmail());
        Usuario teacher = new Usuario("teacher@example.com", "hash", "Teacher", RolUsuario.ROLE_PROFESOR);
        String teacherToken = jwtService.generateToken(teacher);

        service.revoke(expired);
        service.revoke(invalidSignature);
        service.revoke(legacyTokenWithRole(student.getEmail(), RolUsuario.ROLE_PROFESOR.name()));
        service.revoke(teacherToken);

        org.mockito.Mockito.verify(revocationRepository, org.mockito.Mockito.never())
                .insertIfAbsent(anyString(), anyString(), anyString(), any(LocalDateTime.class),
                        any(LocalDateTime.class));
    }

    @Test
    void shouldMakeConcurrentLogoutAttemptsInsertAtMostOneRevocation() throws Exception {
        Set<String> revokedHashes = ConcurrentHashMap.newKeySet();
        AtomicInteger insertAttempts = new AtomicInteger();
        given(revocationRepository.insertIfAbsent(anyString(), anyString(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .willAnswer(invocation -> {
                    insertAttempts.incrementAndGet();
                    return revokedHashes.add(invocation.getArgument(2)) ? 1 : 0;
                });
        String token = jwtService.generateToken(student);
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            for (int i = 0; i < workers; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    service.revoke(token);
                    return null;
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(insertAttempts.get()).isEqualTo(workers);
        assertThat(revokedHashes).hasSize(1);
    }

    private String legacyToken(String subject, long expiresAt) {
        return Jwts.builder()
                .subject(subject)
                .claim("rol", RolUsuario.ROLE_ALUMNO.name())
                .issuedAt(new Date())
                .expiration(new Date(expiresAt))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    private String legacyTokenWithRole(String subject, String role) {
        return Jwts.builder()
                .subject(subject)
                .claim("rol", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    private String legacyTokenWithOtherKey(String subject) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000L))
                .signWith(Keys.hmacShaKeyFor("anothersecretforlocaltestingonlynotproduction".getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
    }
}
