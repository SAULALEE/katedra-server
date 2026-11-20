package Katedra.Server.student.repository;

import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import Katedra.Server.service.JwtService;
import Katedra.Server.student.service.StudentSessionRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-postgresql",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.maximum-pool-size=16"
})
@ActiveProfiles("postgresql")
@Testcontainers(disabledWithoutDocker = true)
class StudentSessionRevocationRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private StudentSessionRevocationService revocationService;

    @Autowired
    private StudentSessionRevocationRepository revocationRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldPersistConcurrentRevocationOnceAndCleanExpiredRowsOnLogout() throws Exception {
        Usuario student = usuarioRepository.save(new Usuario(
                "student-session-" + UUID.randomUUID() + "@katedra.test",
                "hash", "Student", RolUsuario.ROLE_ALUMNO));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        revocationRepository.insertIfAbsent(UUID.randomUUID().toString(), student.getId(), "a".repeat(64),
                now.minusMinutes(1), now.minusMinutes(2));
        revocationService.cleanupExpiredRevocations();
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
                    revocationService.revoke(token);
                    return null;
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(revocationRepository.count()).isEqualTo(1);
        assertThat(revocationService.isRevoked(token)).isTrue();
    }
}
