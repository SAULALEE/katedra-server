package Katedra.Server.dto;

import Katedra.Server.model.CicloFacturacion;
import Katedra.Server.model.EstadoSuscripcion;
import Katedra.Server.model.PlanUsuario;

import java.time.LocalDateTime;

/**
 * The user's current billing state.
 *
 * <p>Carries no Stripe identifiers on purpose: the client never needs them, and leaking a
 * customer or subscription id into the browser only widens what an XSS could do.
 */
public record SuscripcionResponseDTO(
        String id,
        PlanUsuario plan,
        EstadoSuscripcion estado,
        CicloFacturacion ciclo,
        LocalDateTime periodoInicio,
        LocalDateTime periodoFin,
        boolean cancelaAlFinal) {
}
