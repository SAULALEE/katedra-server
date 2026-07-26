package Katedra.Server.controller;

import Katedra.Server.dto.IniciarSuscripcionRequestDTO;
import Katedra.Server.dto.IniciarSuscripcionResponseDTO;
import Katedra.Server.dto.SuscripcionResponseDTO;
import Katedra.Server.dto.UsoPlanResponseDTO;
import Katedra.Server.service.PlanLimitService;
import Katedra.Server.service.SuscripcionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Billing endpoints for the signed-in user.
 *
 * <p>Deliberately not hung off {@code /usuarios/me/...}: that path is restricted to
 * hasRole("ADMIN") in the security chain, so a teacher — the only kind of user who would
 * ever subscribe — would get a 403 on every call.
 */
@RestController
@RequestMapping("/suscripciones")
public class SuscripcionController {

    private final SuscripcionService suscripcionService;
    private final PlanLimitService planLimitService;

    public SuscripcionController(SuscripcionService suscripcionService, PlanLimitService planLimitService) {
        this.suscripcionService = suscripcionService;
        this.planLimitService = planLimitService;
    }

    @GetMapping("/me")
    public ResponseEntity<SuscripcionResponseDTO> miSuscripcion(Authentication authentication) {
        return ResponseEntity.ok(suscripcionService.miSuscripcion(authentication.getName()));
    }

    /** Plan, today's usage and capability flags — everything the sidebar meter needs. */
    @GetMapping("/me/uso")
    public ResponseEntity<UsoPlanResponseDTO> miUso(Authentication authentication) {
        return ResponseEntity.ok(planLimitService.consultarUso(authentication.getName()));
    }

    /** Step one of the checkout. Returns the client secret for Stripe Elements. */
    @PostMapping
    public ResponseEntity<IniciarSuscripcionResponseDTO> iniciar(
            Authentication authentication,
            @Valid @RequestBody IniciarSuscripcionRequestDTO request) {
        return ResponseEntity.ok(suscripcionService.iniciar(authentication.getName(), request));
    }

    /**
     * Called after the browser confirms the payment. The request is only a hint to re-read
     * live state from Stripe — it is never taken as proof that anything was paid.
     */
    @PostMapping("/{id}/confirmar")
    public ResponseEntity<SuscripcionResponseDTO> confirmar(
            Authentication authentication,
            @PathVariable("id") String suscripcionId) {
        return ResponseEntity.ok(suscripcionService.confirmar(authentication.getName(), suscripcionId));
    }

    /** Cancels at the end of the paid period; PRO stays until it runs out. */
    @DeleteMapping("/me")
    public ResponseEntity<SuscripcionResponseDTO> cancelar(Authentication authentication) {
        return ResponseEntity.ok(suscripcionService.cancelar(authentication.getName()));
    }
}
