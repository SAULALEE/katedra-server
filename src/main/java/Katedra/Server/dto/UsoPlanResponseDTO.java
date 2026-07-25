package Katedra.Server.dto;

import Katedra.Server.model.PlanUsuario;

import java.time.LocalDate;

/**
 * Everything the client needs to render the plan badge, the daily usage meter and the
 * feature locks, in one call.
 *
 * <p>The capability flags are sent explicitly rather than letting the client derive them
 * from {@code plan}, so that changing what a tier unlocks is a server-side edit only —
 * a deployed frontend never disagrees with the backend about what it may do.
 */
public record UsoPlanResponseDTO(
        PlanUsuario plan,
        LocalDate fecha,
        int generacionesUsadas,
        int generacionesLimite,
        int exportacionesUsadas,
        int exportacionesLimite,
        boolean permiteModeloPro,
        boolean permiteDiapositivas,
        boolean permiteCargaArchivo,
        boolean permiteCargaUrl) {
}
