package Katedra.Server.dto;

import Katedra.Server.model.PlanUsuario;
import Katedra.Server.model.RolUsuario;

import java.time.LocalDateTime;

/**
 * Carries {@code plan} so the client can paint the right badge on the first render after
 * login, without waiting for {@code /suscripciones/me/uso}. That endpoint remains the
 * authority; this is a convenience copy, and the backend gates never read it.
 */
public record UsuarioDTO(
    String id,
    String email,
    String nombre,
    RolUsuario rol,
    PlanUsuario plan,
    LocalDateTime createdAt
) {
    /** Defaults to FREE: a caller that does not know the plan must never imply PRO. */
    public UsuarioDTO(String id, String email, String nombre, RolUsuario rol) {
        this(id, email, nombre, rol, PlanUsuario.FREE, null);
    }
}
