package Katedra.Server.repository;

import Katedra.Server.model.UsoDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Daily quota accounting.
 *
 * <p>The reservation is two statements rather than one {@code INSERT ... ON DUPLICATE KEY
 * UPDATE}: that construct's return code is ambiguous (1 = insert, 2 = update-with-change,
 * 0 = no-change), its INSERT branch bypasses the limit check entirely, and touching
 * {@code updated_at} unconditionally destroys the "0 means rejected" signal. Split in two,
 * there is exactly one unambiguous integer answer.
 */
public interface UsoDiarioRepository extends JpaRepository<UsoDiario, String> {

    Optional<UsoDiario> findByUsuarioIdAndFecha(String usuarioId, LocalDate fecha);

    /**
     * Ensures today's row exists. Idempotent — the UNIQUE(usuario_id, fecha) constraint
     * absorbs the race between concurrent first-requests of the day, and the loser just
     * falls through to the conditional update.
     *
     * <p>{@code ON DUPLICATE KEY UPDATE usuario_id = usuario_id} is a deliberate no-op
     * update: on conflict nothing is written, so a late caller cannot reset a counter the
     * winner already incremented. The return value is meaningless here (MySQL reports 1
     * for insert, 0 for no-change) and is ignored — the quota decision belongs entirely to
     * the conditional UPDATE below.
     *
     * <p>Native because this has no JPQL equivalent. INSERT IGNORE was the obvious
     * candidate but H2 rejects it outright even in MODE=MySQL, which
     * {@code UsoDiarioRepositoryTest} caught; ON DUPLICATE KEY UPDATE is understood by
     * both engines.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO uso_diario (id, usuario_id, fecha, generaciones, exportaciones, created_at) "
            + "VALUES (:id, :usuarioId, :fecha, 0, 0, CURRENT_TIMESTAMP) "
            + "ON DUPLICATE KEY UPDATE usuario_id = usuario_id", nativeQuery = true)
    int crearFilaSiNoExiste(@Param("id") String id,
                            @Param("usuarioId") String usuarioId,
                            @Param("fecha") LocalDate fecha);

    /**
     * Conditionally consumes quota. The affected-row count IS the answer: 1 means
     * reserved, 0 means the limit would have been exceeded and nothing was written.
     * The check lives in the WHERE clause so the read and the write are a single atomic
     * statement — a read-then-write would let concurrent requests both pass.
     */
    @Modifying
    @Transactional
    @Query("update UsoDiario u set u.generaciones = u.generaciones + :cantidad, u.updatedAt = CURRENT_TIMESTAMP "
            + "where u.usuarioId = :usuarioId and u.fecha = :fecha and u.generaciones + :cantidad <= :limite")
    int consumirGeneraciones(@Param("usuarioId") String usuarioId,
                             @Param("fecha") LocalDate fecha,
                             @Param("cantidad") int cantidad,
                             @Param("limite") int limite);

    /**
     * Refunds quota reserved for AI pieces that ultimately failed.
     *
     * <p>CASE and not GREATEST: GREATEST is not portable JPQL, and this has to run on both
     * MySQL and H2. The clamp at 0 guards against a double refund leaving a negative
     * counter, which would silently hand the user free quota tomorrow.
     */
    @Modifying
    @Transactional
    @Query("update UsoDiario u set u.generaciones = case when u.generaciones - :cantidad < 0 then 0 "
            + "else u.generaciones - :cantidad end, u.updatedAt = CURRENT_TIMESTAMP "
            + "where u.usuarioId = :usuarioId and u.fecha = :fecha")
    int liberarGeneraciones(@Param("usuarioId") String usuarioId,
                            @Param("fecha") LocalDate fecha,
                            @Param("cantidad") int cantidad);

    /** Same contract as {@link #consumirGeneraciones}: 0 affected rows means rejected. */
    @Modifying
    @Transactional
    @Query("update UsoDiario u set u.exportaciones = u.exportaciones + :cantidad, u.updatedAt = CURRENT_TIMESTAMP "
            + "where u.usuarioId = :usuarioId and u.fecha = :fecha and u.exportaciones + :cantidad <= :limite")
    int consumirExportaciones(@Param("usuarioId") String usuarioId,
                              @Param("fecha") LocalDate fecha,
                              @Param("cantidad") int cantidad,
                              @Param("limite") int limite);

    @Modifying
    @Transactional
    @Query("update UsoDiario u set u.exportaciones = case when u.exportaciones - :cantidad < 0 then 0 "
            + "else u.exportaciones - :cantidad end, u.updatedAt = CURRENT_TIMESTAMP "
            + "where u.usuarioId = :usuarioId and u.fecha = :fecha")
    int liberarExportaciones(@Param("usuarioId") String usuarioId,
                             @Param("fecha") LocalDate fecha,
                             @Param("cantidad") int cantidad);
}
