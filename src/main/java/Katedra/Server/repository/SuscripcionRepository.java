package Katedra.Server.repository;

import Katedra.Server.model.EstadoSuscripcion;
import Katedra.Server.model.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, String> {

    /**
     * Lookup by the Stripe id, backed by a UNIQUE index. This is the upsert key that makes
     * syncing from Stripe idempotent regardless of whether the webhook, the client
     * confirmation, or both arrive.
     */
    Optional<Suscripcion> findByStripeSubscriptionId(String stripeSubscriptionId);

    /** Most recent subscription of a user, whatever its state. Drives "mi plan". */
    Optional<Suscripcion> findFirstByUsuarioIdOrderByCreatedAtDesc(String usuarioId);

    /**
     * Used by {@code SuscripcionService.iniciar} to reuse an in-flight checkout instead of
     * creating a second Stripe subscription when the user backs out and resubmits.
     */
    Optional<Suscripcion> findFirstByUsuarioIdAndEstadoOrderByCreatedAtDesc(
            String usuarioId, EstadoSuscripcion estado);
}
