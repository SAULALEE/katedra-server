package Katedra.Server.service;

import Katedra.Server.model.CicloFacturacion;
import com.stripe.StripeClient;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Covers the parts of the Stripe wrapper that do not need the network: webhook payload
 * handling, price resolution and the SDK's own response shape.
 *
 * <p>Exercises the real {@code Webhook.constructEvent} rather than a mocked
 * {@code StripeService}. That distinction matters — a mocked wrapper cannot reveal which
 * exceptions the SDK actually throws, and that is exactly how the malformed-payload 500
 * below went unnoticed until the endpoint was probed with curl.
 */
@ExtendWith(MockitoExtension.class)
class StripeServiceTest {

    @Mock
    private StripeClient stripeClient;

    private StripeService stripeService;

    @BeforeEach
    void setUp() {
        stripeService = new StripeService(
                stripeClient, "pk_test_dummy", "whsec_dummy", "price_mensual", "price_anual");
    }

    // --- webhook payload handling ---

    @Test
    @DisplayName("A forged signature is a 400, never a 500")
    void firmaFalsificadaEs400() {
        ResponseStatusException ex = (ResponseStatusException) catchThrowable(() ->
                stripeService.verificarEvento("{\"id\":\"evt_1\",\"type\":\"invoice.paid\"}",
                        "t=1,v1=firma_falsificada"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("A malformed payload is a 400, never a 500")
    void cuerpoIlegibleEs400() {
        // constructEvent parses the JSON before verifying the signature, so junk comes back
        // as an unchecked JsonSyntaxException. This endpoint is permitAll and reachable by
        // anyone on the internet; a 500 here would mean any junk POST produces a stack
        // trace. Found by probing the running app, not by the mocked controller test.
        ResponseStatusException ex = (ResponseStatusException) catchThrowable(() ->
                stripeService.verificarEvento("esto no es json", "t=1,v1=loquesea"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("An empty payload is a 400, never a 500")
    void cuerpoVacioEs400() {
        ResponseStatusException ex = (ResponseStatusException) catchThrowable(() ->
                stripeService.verificarEvento("", "t=1,v1=loquesea"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("A malformed signature header is a 400, never a 500")
    void cabeceraDeFirmaMalformadaEs400() {
        ResponseStatusException ex = (ResponseStatusException) catchThrowable(() ->
                stripeService.verificarEvento("{\"id\":\"evt_1\"}", "basura"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- price resolution ---

    @Test
    @DisplayName("Each billing period resolves to its own configured price")
    void cadaCicloResuelveSuPrecio() {
        assertThat(stripeService.resolverPriceId(CicloFacturacion.MENSUAL)).isEqualTo("price_mensual");
        assertThat(stripeService.resolverPriceId(CicloFacturacion.ANUAL)).isEqualTo("price_anual");
    }

    @Test
    @DisplayName("An unconfigured price is a 503, not an empty id sent to Stripe")
    void precioSinConfigurarEs503() {
        // A deployment problem, not a user error. Failing here gives a clear signal
        // instead of letting Stripe reject an empty price id with a cryptic message.
        StripeService sinPrecios = new StripeService(stripeClient, "pk", "whsec", "", "");

        ResponseStatusException ex = (ResponseStatusException) catchThrowable(() ->
                sinPrecios.resolverPriceId(CicloFacturacion.MENSUAL));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    // --- SDK response shape ---

    @Test
    @DisplayName("The client secret is read from latest_invoice.confirmation_secret")
    void extraeElClientSecretDeLaFactura() {
        Invoice.ConfirmationSecret secreto = new Invoice.ConfirmationSecret();
        secreto.setClientSecret("pi_abc_secret_xyz");
        Invoice invoice = new Invoice();
        invoice.setConfirmationSecret(secreto);
        Subscription sub = new Subscription();
        sub.setLatestInvoiceObject(invoice);

        assertThat(stripeService.extraerClientSecret(sub)).isEqualTo("pi_abc_secret_xyz");
    }

    @Test
    @DisplayName("A subscription with no client secret is a 502, not a null handed to the browser")
    void sinClientSecretEs502() {
        Subscription sub = new Subscription();
        sub.setId("sub_1");

        ResponseStatusException ex = (ResponseStatusException) catchThrowable(() ->
                stripeService.extraerClientSecret(sub));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("Period bounds are read from the subscription item, not the subscription")
    void elPeriodoSeLeeDelItem() {
        // Stripe moved current_period_start/end onto items in the basil API version, which
        // is what stripe-java 29.2.0 pins. Reading them off Subscription would silently
        // yield nulls and blank the "renews on" date in the UI.
        SubscriptionItem item = new SubscriptionItem();
        item.setCurrentPeriodStart(1785000000L);
        item.setCurrentPeriodEnd(1787678400L);
        SubscriptionItemCollection items = new SubscriptionItemCollection();
        items.setData(List.of(item));
        Subscription sub = new Subscription();
        sub.setItems(items);

        LocalDateTime[] periodo = stripeService.periodoDe(sub);

        assertThat(periodo[0]).isNotNull();
        assertThat(periodo[1]).isNotNull();
        assertThat(periodo[0]).isBefore(periodo[1]);
    }

    @Test
    @DisplayName("A subscription with no items yields nulls instead of throwing")
    void sinItemsDevuelveNulos() {
        LocalDateTime[] periodo = stripeService.periodoDe(new Subscription());

        assertThat(periodo).containsExactly(null, null);
    }
}
