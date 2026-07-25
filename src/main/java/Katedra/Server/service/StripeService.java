package Katedra.Server.service;

import Katedra.Server.dto.DatosFacturacionDTO;
import Katedra.Server.model.CicloFacturacion;
import Katedra.Server.model.Usuario;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionRetrieveParams;
import com.stripe.param.SubscriptionUpdateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Thin wrapper over the Stripe SDK. Deliberately holds no database state, so
 * {@code SuscripcionServiceTest} can mock it wholesale and never reach the network.
 *
 * <p>Every method translates {@link StripeException} into a {@link ResponseStatusException}
 * with a generic Spanish message. Stripe's own error text is logged but never surfaced:
 * {@code server.error.include-message=always} means a reason string is returned verbatim
 * to the browser, and provider errors can name internal ids and account details.
 */
@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final StripeClient stripeClient;
    private final String publishableKey;
    private final String webhookSecret;
    private final String priceMensual;
    private final String priceAnual;

    public StripeService(StripeClient stripeClient,
                         @Value("${stripe.publishable-key:}") String publishableKey,
                         @Value("${stripe.webhook-secret:}") String webhookSecret,
                         @Value("${stripe.price.pro-mensual:}") String priceMensual,
                         @Value("${stripe.price.pro-anual:}") String priceAnual) {
        this.stripeClient = stripeClient;
        this.publishableKey = publishableKey;
        this.webhookSecret = webhookSecret;
        this.priceMensual = priceMensual;
        this.priceAnual = priceAnual;
    }

    public String getPublishableKey() {
        return publishableKey;
    }

    /** Resolves the configured Stripe Price for a billing period. */
    public String resolverPriceId(CicloFacturacion ciclo) {
        String priceId = ciclo == CicloFacturacion.ANUAL ? priceAnual : priceMensual;
        if (priceId == null || priceId.isBlank()) {
            // A misconfigured deployment, not a user error: fail loudly rather than
            // letting Stripe reject an empty price id with a confusing message.
            log.error("Falta el price id de Stripe para el ciclo {}", ciclo);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Los pagos no están disponibles en este momento.");
        }
        return priceId;
    }

    /**
     * Returns the Stripe Customer id for this user, creating it the first time and
     * refreshing its billing details on later checkouts.
     *
     * <p>Never creates a second customer for the same user: a duplicate would split their
     * payment history and detach saved payment methods from future invoices.
     */
    public String asegurarCustomer(Usuario usuario, DatosFacturacionDTO datos, String customerIdExistente) {
        try {
            if (customerIdExistente != null && !customerIdExistente.isBlank()) {
                stripeClient.customers().update(customerIdExistente, CustomerUpdateParams.builder()
                        .setName(datos.nombreCompleto())
                        .setEmail(datos.email())
                        .setAddress(direccionUpdate(datos))
                        .build());
                return customerIdExistente;
            }

            Customer customer = stripeClient.customers().create(CustomerCreateParams.builder()
                    .setName(datos.nombreCompleto())
                    .setEmail(datos.email())
                    .setAddress(direccionCreate(datos))
                    // Lets a human working in the Stripe dashboard trace a payment back to
                    // an account without a database lookup.
                    .putMetadata("usuarioId", usuario.getId())
                    .putMetadata("usuarioEmail", usuario.getEmail())
                    .build());
            return customer.getId();
        } catch (StripeException e) {
            throw traducir(e, "No pudimos registrar tus datos de facturación.");
        }
    }

    /**
     * Creates a subscription that is intentionally left unpaid.
     *
     * <p>{@code DEFAULT_INCOMPLETE} is what makes a custom checkout possible: Stripe
     * creates the subscription and its first invoice but waits, handing back a client
     * secret the browser confirms with our own card form. Access is granted only once that
     * confirmation is verified server-side.
     *
     * <p>Card is the only payment method type on purpose — enabling redirect-based methods
     * would break the "everything happens inside the modal" flow.
     */
    public Subscription crearSuscripcionIncompleta(String customerId, String priceId, String usuarioId) {
        try {
            return stripeClient.subscriptions().create(SubscriptionCreateParams.builder()
                    .setCustomer(customerId)
                    .addItem(SubscriptionCreateParams.Item.builder().setPrice(priceId).build())
                    .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                    .setPaymentSettings(SubscriptionCreateParams.PaymentSettings.builder()
                            .setSaveDefaultPaymentMethod(
                                    SubscriptionCreateParams.PaymentSettings.SaveDefaultPaymentMethod.ON_SUBSCRIPTION)
                            .addPaymentMethodType(
                                    SubscriptionCreateParams.PaymentSettings.PaymentMethodType.CARD)
                            .build())
                    .addAllExpand(List.of("latest_invoice.confirmation_secret"))
                    .putMetadata("usuarioId", usuarioId)
                    .build());
        } catch (StripeException e) {
            throw traducir(e, "No pudimos iniciar el pago. Inténtalo de nuevo.");
        }
    }

    /**
     * Pulls the client secret out of an incomplete subscription's first invoice.
     *
     * <p>Reached through {@code latest_invoice.confirmation_secret}, which is the accessor
     * available from Stripe API version 2025-03-31.basil onward — this project is pinned to
     * 2025-05-28.basil by stripe-java 29.2.0. On older API versions the equivalent lived at
     * {@code latest_invoice.payment_intent.client_secret}, so this is the method to revisit
     * if the SDK is ever downgraded.
     */
    public String extraerClientSecret(Subscription subscription) {
        Invoice invoice = subscription.getLatestInvoiceObject();
        if (invoice == null || invoice.getConfirmationSecret() == null
                || invoice.getConfirmationSecret().getClientSecret() == null) {
            log.error("Stripe no devolvió confirmation_secret para la suscripción {}", subscription.getId());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No pudimos preparar el pago. Inténtalo de nuevo.");
        }
        return invoice.getConfirmationSecret().getClientSecret();
    }

    /** Live state of a subscription. The only source of truth for granting or revoking PRO. */
    public Subscription recuperarSuscripcion(String stripeSubscriptionId) {
        try {
            return stripeClient.subscriptions().retrieve(stripeSubscriptionId,
                    SubscriptionRetrieveParams.builder().addExpand("latest_invoice").build());
        } catch (StripeException e) {
            throw traducir(e, "No pudimos consultar tu suscripción.");
        }
    }

    /**
     * Schedules cancellation at the end of the paid period rather than immediately: the
     * user already paid for the current period and should keep PRO until it runs out.
     */
    public Subscription cancelarAlFinalDelPeriodo(String stripeSubscriptionId) {
        try {
            return stripeClient.subscriptions().update(stripeSubscriptionId,
                    SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(true).build());
        } catch (StripeException e) {
            throw traducir(e, "No pudimos cancelar tu suscripción.");
        }
    }

    /**
     * Verifies a webhook signature and parses the event.
     *
     * <p>Takes the raw payload string: the signature is computed over the exact bytes
     * Stripe sent, so deserializing and re-serializing anywhere upstream invalidates it.
     */
    public Event verificarEvento(String payload, String firma) {
        try {
            return Webhook.constructEvent(payload, firma, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Webhook de Stripe con firma inválida: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Firma inválida");
        } catch (RuntimeException e) {
            // constructEvent parses the JSON *before* checking the signature, so a
            // malformed body escapes as an unchecked JsonSyntaxException rather than a
            // SignatureVerificationException. This endpoint is permitAll and reachable by
            // anyone, so anything unparseable has to come back as 400 — letting it become
            // a 500 would turn a junk POST into a stack trace and a noisy error log.
            log.warn("Webhook de Stripe con cuerpo ilegible: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Petición inválida");
        }
    }

    /**
     * Current period bounds of a subscription.
     *
     * <p>Read from the first subscription item, not from the subscription: Stripe moved
     * {@code current_period_start/end} onto items in the basil API version. Verified
     * against the 29.2.0 jar — {@code Subscription} no longer exposes them at all.
     */
    public LocalDateTime[] periodoDe(Subscription subscription) {
        if (subscription.getItems() == null || subscription.getItems().getData() == null
                || subscription.getItems().getData().isEmpty()) {
            return new LocalDateTime[]{null, null};
        }
        SubscriptionItem item = subscription.getItems().getData().get(0);
        return new LocalDateTime[]{aFecha(item.getCurrentPeriodStart()), aFecha(item.getCurrentPeriodEnd())};
    }

    private LocalDateTime aFecha(Long epochSegundos) {
        return epochSegundos == null
                ? null
                : LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSegundos), ZoneId.systemDefault());
    }

    private CustomerCreateParams.Address direccionCreate(DatosFacturacionDTO datos) {
        return CustomerCreateParams.Address.builder()
                .setCountry(datos.pais())
                .setCity(datos.ciudad())
                .setLine1(datos.direccion())
                .setPostalCode(datos.codigoPostal())
                .build();
    }

    private CustomerUpdateParams.Address direccionUpdate(DatosFacturacionDTO datos) {
        return CustomerUpdateParams.Address.builder()
                .setCountry(datos.pais())
                .setCity(datos.ciudad())
                .setLine1(datos.direccion())
                .setPostalCode(datos.codigoPostal())
                .build();
    }

    /**
     * Logs the provider's own message and returns a generic one for the user. Stripe error
     * text can name internal ids and account details, and this project returns reason
     * strings verbatim to the browser.
     */
    private ResponseStatusException traducir(StripeException e, String mensajeUsuario) {
        log.error("Error de Stripe: {}", e.getMessage(), e);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, mensajeUsuario);
    }
}
