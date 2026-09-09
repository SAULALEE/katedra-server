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
 * <p>The row creation is delegated to the profile-specific {@link UsoDiarioUpsert}.
 * Conditional quota updates remain portable JPQL, and their affected-row count is the
 * unambiguous reservation result.
 */
public interface UsoDiarioRepository extends JpaRepository<UsoDiario, String> {

    Optional<UsoDiario> findByUsuarioIdAndFecha(String usuarioId, LocalDate fecha);

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
