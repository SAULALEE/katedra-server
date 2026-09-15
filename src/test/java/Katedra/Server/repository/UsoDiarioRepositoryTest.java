package Katedra.Server.repository;

import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.UsoDiario;
import Katedra.Server.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the quota SQL against a real database rather than mocks.
 *
 * <p>Worth the setup cost because the entire quota design rests on one claim that a mock
 * cannot verify: that "0 affected rows" is a reliable rejection signal, and that the limit
 * check inside the WHERE clause really is atomic with the increment.
 *
 * <p>Runs on PostgreSQL because the upsert uses {@code ON CONFLICT}; H2 cannot model
 * that contract accurately enough for quota accounting.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-postgresql",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.maximum-pool-size=16"
})
@ActiveProfiles("postgresql")
@Testcontainers(disabledWithoutDocker = true)
class UsoDiarioRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private UsoDiarioRepository usoDiarioRepository;

    @Autowired
    private UsoDiarioUpsert usoDiarioUpsert;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private String usuarioId;
    private final LocalDate hoy = LocalDate.of(2026, 7, 25);

    @BeforeEach
    void setUp() {
        Usuario usuario = new Usuario(
                "docente-" + UUID.randomUUID() + "@katedra.test",
                "hash",
                "Docente",
                RolUsuario.ROLE_PROFESOR);
        usuarioId = usuarioRepository.save(usuario).getId();
    }

    private void crearFilaDeHoy() {
        usoDiarioUpsert.crearFilaSiNoExiste(UUID.randomUUID().toString(), usuarioId, hoy);
    }

    private int generacionesDeHoy() {
        return usoDiarioRepository.findByUsuarioIdAndFecha(usuarioId, hoy)
                .map(UsoDiario::getGeneraciones)
                .orElseThrow();
    }

    private long filasDeHoy() {
        return usoDiarioRepository.findAll().stream()
                .filter(uso -> usuarioId.equals(uso.getUsuarioId()) && hoy.equals(uso.getFecha()))
                .count();
    }

    @Test
    @DisplayName("crearFilaSiNoExiste is idempotent: a second call does not duplicate or reset the row")
    void crearFilaSiNoExisteEsIdempotente() {
        crearFilaDeHoy();
        usoDiarioRepository.consumirGeneraciones(usuarioId, hoy, 4, 10);

        // Simulates the concurrent-first-request race: the loser calls this after the
        // winner has already consumed quota. It must not wipe the counter.
        crearFilaDeHoy();

        assertThat(generacionesDeHoy()).isEqualTo(4);
        assertThat(filasDeHoy()).isEqualTo(1);
    }

    @Test
    @DisplayName("consumirGeneraciones reserves quota and reports 1 affected row")
    void consumirGeneracionesReserva() {
        crearFilaDeHoy();

        assertThat(usoDiarioRepository.consumirGeneraciones(usuarioId, hoy, 3, 10)).isEqualTo(1);
        assertThat(generacionesDeHoy()).isEqualTo(3);
    }

    @Test
    @DisplayName("consumirGeneraciones allows consuming exactly up to the limit")
    void consumirGeneracionesPermiteLlegarAlLimiteExacto() {
        crearFilaDeHoy();
        usoDiarioRepository.consumirGeneraciones(usuarioId, hoy, 7, 10);

        assertThat(usoDiarioRepository.consumirGeneraciones(usuarioId, hoy, 3, 10)).isEqualTo(1);
        assertThat(generacionesDeHoy()).isEqualTo(10);
    }

    @Test
    @DisplayName("consumirGeneraciones rejects with 0 rows and writes nothing when the limit would be exceeded")
    void consumirGeneracionesRechazaYNoEscribe() {
        crearFilaDeHoy();
        usoDiarioRepository.consumirGeneraciones(usuarioId, hoy, 9, 10);

        // The rejection must be all-or-nothing: no partial reservation of the 1 remaining.
        assertThat(usoDiarioRepository.consumirGeneraciones(usuarioId, hoy, 2, 10)).isZero();
        assertThat(generacionesDeHoy()).isEqualTo(9);
    }

    @Test
    @DisplayName("liberarGeneraciones refunds only the failed pieces")
    void liberarGeneracionesDevuelve() {
        crearFilaDeHoy();
        usoDiarioRepository.consumirGeneraciones(usuarioId, hoy, 4, 10);

        usoDiarioRepository.liberarGeneraciones(usuarioId, hoy, 3);

        assertThat(generacionesDeHoy()).isEqualTo(1);
    }

    @Test
    @DisplayName("liberarGeneraciones clamps at zero so a double refund cannot gift tomorrow's quota")
    void liberarGeneracionesNoBajaDeCero() {
        crearFilaDeHoy();
        usoDiarioRepository.consumirGeneraciones(usuarioId, hoy, 2, 10);

        usoDiarioRepository.liberarGeneraciones(usuarioId, hoy, 5);

        assertThat(generacionesDeHoy()).isZero();
    }

    @Test
    @DisplayName("quota is scoped per day: yesterday's usage does not count against today")
    void laCuotaEsPorDia() {
        LocalDate ayer = hoy.minusDays(1);
        usoDiarioUpsert.crearFilaSiNoExiste(UUID.randomUUID().toString(), usuarioId, ayer);
        usoDiarioRepository.consumirGeneraciones(usuarioId, ayer, 10, 10);

        crearFilaDeHoy();

        assertThat(usoDiarioRepository.consumirGeneraciones(usuarioId, hoy, 10, 10)).isEqualTo(1);
    }

    @Test
    @DisplayName("generaciones and exportaciones are independent counters")
    void contadoresIndependientes() {
        crearFilaDeHoy();
        usoDiarioRepository.consumirGeneraciones(usuarioId, hoy, 10, 10);

        assertThat(usoDiarioRepository.consumirExportaciones(usuarioId, hoy, 1, 5)).isEqualTo(1);
    }

    @Test
    @DisplayName("consumirExportaciones rejects with 0 rows past its own limit")
    void consumirExportacionesRechaza() {
        crearFilaDeHoy();
        usoDiarioRepository.consumirExportaciones(usuarioId, hoy, 5, 5);

        assertThat(usoDiarioRepository.consumirExportaciones(usuarioId, hoy, 1, 5)).isZero();
    }
}
