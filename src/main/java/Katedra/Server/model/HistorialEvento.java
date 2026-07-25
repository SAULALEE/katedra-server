package Katedra.Server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * A single, immutable entry in a user's content history: temario created, edited,
 * (un)favorited, had material generated, or deleted.
 *
 * <p>Append-only by design — unlike {@link Temario}, there is deliberately no soft-delete
 * machinery here, because nothing is ever allowed to remove an audit entry, not even the
 * event's own owner. {@code temarioTitulo}/{@code asignaturaNombre} are snapshots taken at
 * the moment of the event rather than a live join to {@link Temario}, so the row stays
 * readable even after the temario itself is later soft-deleted.
 */
@Entity
@Table(name = "historial_evento")
public class HistorialEvento {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    @Column(name = "usuario_id", nullable = false, updatable = false, length = 36)
    private String usuarioId;

    @Column(name = "temario_id", updatable = false, length = 36)
    private String temarioId;

    @Column(name = "temario_titulo", nullable = false, updatable = false)
    private String temarioTitulo;

    @Column(name = "asignatura_nombre", updatable = false)
    private String asignaturaNombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 30)
    private TipoEventoHistorial tipo;

    @Column(name = "detalle", updatable = false, length = 500)
    private String detalle;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected HistorialEvento() {
    }

    public HistorialEvento(
            String usuarioId,
            String temarioId,
            String temarioTitulo,
            String asignaturaNombre,
            TipoEventoHistorial tipo,
            String detalle) {
        this.usuarioId = usuarioId;
        this.temarioId = temarioId;
        this.temarioTitulo = temarioTitulo;
        this.asignaturaNombre = asignaturaNombre;
        this.tipo = tipo;
        this.detalle = detalle;
    }

    public String getId() {
        return id;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public String getTemarioId() {
        return temarioId;
    }

    public String getTemarioTitulo() {
        return temarioTitulo;
    }

    public String getAsignaturaNombre() {
        return asignaturaNombre;
    }

    public TipoEventoHistorial getTipo() {
        return tipo;
    }

    public String getDetalle() {
        return detalle;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
