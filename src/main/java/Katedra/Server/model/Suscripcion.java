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
 * A user's billing relationship with Stripe. One row per Stripe subscription; a user who
 * cancels and resubscribes accumulates rows, so this doubles as billing history.
 *
 * <p>Deliberately no {@code @SQLDelete}/{@code @SQLRestriction}: a billing record is never
 * deleted, not even when the user is. Cancellation is a state, not a removal.
 *
 * <p>{@code usuarioId} is a plain column rather than a {@code @ManyToOne}, matching
 * {@link HistorialEvento}. Two reasons: rows are written from the Stripe webhook thread
 * where a LAZY proxy is a hazard, and nothing here ever needs to navigate to the user.
 * A relation would also inherit {@link Usuario}'s {@code deleted_at IS NULL} restriction,
 * which would silently hide the subscriptions of soft-deleted users from billing queries.
 *
 * <p>{@code stripeSubscriptionId} is UNIQUE at the DB level. That constraint is what makes
 * {@code SuscripcionService.sincronizarDesdeStripe} safely idempotent when the webhook and
 * the client-confirm path both fire for the same payment.
 */
@Entity
@Table(name = "suscripcion")
public class Suscripcion {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    @Column(name = "usuario_id", nullable = false, updatable = false, length = 36)
    private String usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false)
    private PlanUsuario plan = PlanUsuario.PRO;

    @Enumerated(EnumType.STRING)
    @Column(name = "ciclo", nullable = false)
    private CicloFacturacion ciclo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoSuscripcion estado = EstadoSuscripcion.INCOMPLETA;

    @Column(name = "stripe_customer_id", nullable = false)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", nullable = false)
    private String stripeSubscriptionId;

    @Column(name = "stripe_price_id", nullable = false)
    private String stripePriceId;

    @Column(name = "periodo_inicio")
    private LocalDateTime periodoInicio;

    @Column(name = "periodo_fin")
    private LocalDateTime periodoFin;

    @Column(name = "cancela_al_final", nullable = false)
    private boolean cancelaAlFinal = false;

    @Column(name = "cancelada_en")
    private LocalDateTime canceladaEn;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected Suscripcion() {
    }

    public Suscripcion(String usuarioId, CicloFacturacion ciclo, String stripeCustomerId,
                       String stripeSubscriptionId, String stripePriceId) {
        this.usuarioId = usuarioId;
        this.ciclo = ciclo;
        this.stripeCustomerId = stripeCustomerId;
        this.stripeSubscriptionId = stripeSubscriptionId;
        this.stripePriceId = stripePriceId;
    }

    public String getId() { return id; }

    public String getUsuarioId() { return usuarioId; }

    public PlanUsuario getPlan() { return plan; }
    public void setPlan(PlanUsuario plan) { this.plan = plan; }

    public CicloFacturacion getCiclo() { return ciclo; }
    public void setCiclo(CicloFacturacion ciclo) { this.ciclo = ciclo; }

    public EstadoSuscripcion getEstado() { return estado; }
    public void setEstado(EstadoSuscripcion estado) { this.estado = estado; }

    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }

    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }

    public String getStripePriceId() { return stripePriceId; }
    public void setStripePriceId(String stripePriceId) { this.stripePriceId = stripePriceId; }

    public LocalDateTime getPeriodoInicio() { return periodoInicio; }
    public void setPeriodoInicio(LocalDateTime periodoInicio) { this.periodoInicio = periodoInicio; }

    public LocalDateTime getPeriodoFin() { return periodoFin; }
    public void setPeriodoFin(LocalDateTime periodoFin) { this.periodoFin = periodoFin; }

    public boolean isCancelaAlFinal() { return cancelaAlFinal; }
    public void setCancelaAlFinal(boolean cancelaAlFinal) { this.cancelaAlFinal = cancelaAlFinal; }

    public LocalDateTime getCanceladaEn() { return canceladaEn; }
    public void setCanceladaEn(LocalDateTime canceladaEn) { this.canceladaEn = canceladaEn; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
