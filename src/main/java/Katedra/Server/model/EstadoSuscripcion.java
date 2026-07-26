package Katedra.Server.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Local projection of a Stripe subscription status.
 *
 * <p>Stripe has seven statuses; Katedra only needs to know four things: the payment has
 * not completed yet, it is paying, it stopped paying, or it is over. Collapsing them here
 * means the rest of the codebase never string-compares raw Stripe values.
 */
public enum EstadoSuscripcion {
    /** Created but the first payment has not been confirmed. Grants nothing. */
    INCOMPLETA("incompleta"),
    /** Paying. The only state that grants PRO. */
    ACTIVA("activa"),
    /** A renewal failed. Access is revoked until Stripe recovers the payment. */
    IMPAGA("impaga"),
    /** Ended, by the user or by Stripe after exhausting retries. */
    CANCELADA("cancelada");

    private final String valor;

    EstadoSuscripcion(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    /** Whether this state entitles the user to {@link PlanUsuario#PRO}. */
    public boolean esVigente() {
        return this == ACTIVA;
    }

    /**
     * Maps a raw Stripe {@code subscription.status}.
     *
     * <p>{@code trialing} maps to ACTIVA because a trial is entitled access even though no
     * money has moved. Unknown values map to INCOMPLETA and never to ACTIVA: if Stripe
     * ever adds a status we do not know about, the safe failure is to withhold access,
     * not to grant it.
     */
    public static EstadoSuscripcion fromStripe(String status) {
        if (status == null) {
            return INCOMPLETA;
        }
        return switch (status) {
            case "active", "trialing" -> ACTIVA;
            case "past_due", "unpaid" -> IMPAGA;
            case "canceled" -> CANCELADA;
            default -> INCOMPLETA;
        };
    }
}
