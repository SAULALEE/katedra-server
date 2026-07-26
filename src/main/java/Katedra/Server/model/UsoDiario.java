package Katedra.Server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row per user per day, holding that day's consumption against the plan limits.
 *
 * <p>Chosen over a {@code (fecha, contador)} column pair on {@link Usuario} for three
 * reasons: the day rollover is implicit (a new date is a new row, so there is no
 * read-compare-reset that could race at midnight), it yields usage history for free, and
 * it keeps {@code usuario} lean — a table the JWT filter hits with {@code findByEmail} on
 * every single request.
 *
 * <p>The {@code UNIQUE(usuario_id, fecha)} constraint is what lets the row be created with
 * a blind {@code INSERT IGNORE}: concurrent first-requests of the day race harmlessly, and
 * the loser simply proceeds to the conditional update.
 *
 * <p>Never mutated through this entity — {@code UsoDiarioRepository} writes it with
 * conditional UPDATEs whose affected-row count is the quota decision. Loading, comparing
 * and saving would reintroduce the lost-update race this table exists to avoid.
 */
@Entity
@Table(
        name = "uso_diario",
        // Declared here and not only in V25: this constraint is the concurrency control
        // for the whole quota mechanism, so it must exist in any schema generated from the
        // entities too. Without it the upsert silently inserts duplicate rows per day.
        uniqueConstraints = @UniqueConstraint(
                name = "uq_uso_diario_usuario_fecha",
                columnNames = {"usuario_id", "fecha"}))
public class UsoDiario {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    @Column(name = "usuario_id", nullable = false, updatable = false, length = 36)
    private String usuarioId;

    @Column(name = "fecha", nullable = false, updatable = false)
    private LocalDate fecha;

    @Column(name = "generaciones", nullable = false)
    private int generaciones;

    @Column(name = "exportaciones", nullable = false)
    private int exportaciones;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected UsoDiario() {
    }

    public String getId() { return id; }

    public String getUsuarioId() { return usuarioId; }

    public LocalDate getFecha() { return fecha; }

    public int getGeneraciones() { return generaciones; }

    public int getExportaciones() { return exportaciones; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
