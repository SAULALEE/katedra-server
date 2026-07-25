package Katedra.Server.dto;

import Katedra.Server.model.CicloFacturacion;

/**
 * Hands the client everything it needs to mount Stripe Elements and confirm the payment.
 *
 * <p>The publishable key travels in the response rather than being baked into the frontend
 * bundle so that rotating it, or pointing a deployment at a different Stripe account, does
 * not require a frontend rebuild.
 */
public record IniciarSuscripcionResponseDTO(
        String suscripcionId,
        String clientSecret,
        String publishableKey,
        CicloFacturacion ciclo) {
}
