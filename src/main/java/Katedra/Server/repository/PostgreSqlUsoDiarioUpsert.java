package Katedra.Server.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;

@Repository
@Profile({"postgresql", "supabase"})
public class PostgreSqlUsoDiarioUpsert implements UsoDiarioUpsert {

    private static final String SQL = """
            INSERT INTO uso_diario
                (id, usuario_id, fecha, generaciones, exportaciones, created_at)
            VALUES (?, ?, ?, 0, 0, CURRENT_TIMESTAMP)
            ON CONFLICT (usuario_id, fecha) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlUsoDiarioUpsert(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public int crearFilaSiNoExiste(String id, String usuarioId, LocalDate fecha) {
        return jdbcTemplate.update(SQL, id, usuarioId, Date.valueOf(fecha));
    }
}
