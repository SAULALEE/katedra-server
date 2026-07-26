package Katedra.Server.controller;

import Katedra.Server.service.StripeService;
import Katedra.Server.service.SuscripcionService;
import com.stripe.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Receives Stripe's server-to-server notifications.
 *
 * <p>Unauthenticated by necessity — Stripe carries no JWT — so the signature check in
 * {@link StripeService#verificarEvento} is the only thing standing between this endpoint
 * and the open internet. It must never be bypassed.
 *
 * <p>This is not the sole activation route: the browser also confirms directly after
 * payment. Both converge on the same idempotent sync, so the feature works with no public
 * webhook URL (a laptop with no tunnel), and both firing cannot double-grant.
 */
@RestController
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final SuscripcionService suscripcionService;
    private final StripeService stripeService;

    public StripeWebhookController(SuscripcionService suscripcionService, StripeService stripeService) {
        this.suscripcionService = suscripcionService;
        this.stripeService = stripeService;
    }

    /**
     * @param payload the raw request body as a String, never a DTO or Map. The signature is
     *                computed over the exact bytes Stripe sent, so letting Spring
     *                deserialize and re-serialize the body would invalidate it.
     */
    @PostMapping
    public ResponseEntity<Void> recibir(@RequestBody String payload,
                                        @RequestHeader("Stripe-Signature") String firma) {
        Event event;
        try {
            event = stripeService.verificarEvento(payload, firma);
        } catch (ResponseStatusException e) {
            // An invalid signature is the only thing that earns a 4xx here.
            return ResponseEntity.badRequest().build();
        }

        try {
            suscripcionService.procesarEvento(event);
        } catch (RuntimeException e) {
            // Acknowledge anyway. A 5xx makes Stripe retry with backoff for days, so a bug
            // on our side would become a self-inflicted retry storm. The failure is logged
            // and the next sync — webhook or client confirmation — reconciles state.
            log.error("Error procesando el evento {} de Stripe", event.getId(), e);
        }

        return ResponseEntity.ok().build();
    }
}
