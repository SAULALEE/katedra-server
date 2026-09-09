package Katedra.Server.repository;

import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.UsoDiario;
import Katedra.Server.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-postgresql",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.maximum-pool-size=24"
})
@ActiveProfiles("postgresql")
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlUsoDiarioUpsertTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private UsoDiarioUpsert usoDiarioUpsert;

    @Autowired
    private UsoDiarioRepository usoDiarioRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private String usuarioId;
    private LocalDate fecha;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        Usuario usuario = new Usuario(
                "upsert-" + suffix + "@katedra.test",
                "hash",
                "Docente",
                RolUsuario.ROLE_PROFESOR);
        usuarioId = usuarioRepository.saveAndFlush(usuario).getId();
        fecha = LocalDate.of(2026, 9, 7);
    }

    @Test
    void crearFilaSiNoExisteEsIdempotenteEnPostgresql() {
        crearFila();
        crearFila();

        assertThat(filasDelDia()).isEqualTo(1);
    }

    @Test
    void conflictoNoReiniciaContadoresEnPostgresql() {
        crearFila();
        assertThat(usoDiarioRepository.consumirGeneraciones(usuarioId, fecha, 4, 10)).isEqualTo(1);

        crearFila();

        assertThat(generacionesDelDia()).isEqualTo(4);
        assertThat(filasDelDia()).isEqualTo(1);
    }

    @Test
    void solicitudesConcurrentesCreanUnaFilaYRespetanElLimite() throws Exception {
        int solicitudes = 20;
        int limite = 10;
        ExecutorService executor = Executors.newFixedThreadPool(solicitudes);
        CountDownLatch listas = new CountDownLatch(solicitudes);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();

        try {
            for (int i = 0; i < solicitudes; i++) {
                resultados.add(executor.submit(() -> {
                    listas.countDown();
                    inicio.await();
                    crearFila();
                    return usoDiarioRepository.consumirGeneraciones(usuarioId, fecha, 1, limite);
                }));
            }

            assertThat(listas.await(10, TimeUnit.SECONDS)).isTrue();
            inicio.countDown();

            int aceptadas = 0;
            for (Future<Integer> resultado : resultados) {
                aceptadas += resultado.get(30, TimeUnit.SECONDS);
            }

            assertThat(aceptadas).isEqualTo(limite);
            assertThat(filasDelDia()).isEqualTo(1);
            assertThat(generacionesDelDia()).isEqualTo(limite);
        } finally {
            inicio.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void crearFila() {
        usoDiarioUpsert.crearFilaSiNoExiste(UUID.randomUUID().toString(), usuarioId, fecha);
    }

    private long filasDelDia() {
        return usoDiarioRepository.findAll().stream()
                .filter(uso -> usuarioId.equals(uso.getUsuarioId()) && fecha.equals(uso.getFecha()))
                .count();
    }

    private int generacionesDelDia() {
        return usoDiarioRepository.findByUsuarioIdAndFecha(usuarioId, fecha)
                .map(UsoDiario::getGeneraciones)
                .orElseThrow();
    }
}
