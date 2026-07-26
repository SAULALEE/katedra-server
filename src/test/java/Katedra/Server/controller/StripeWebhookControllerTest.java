package Katedra.Server.controller;

import Katedra.Server.service.StripeService;
import Katedra.Server.service.SuscripcionService;
import com.stripe.model.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StripeWebhookController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class StripeWebhookControllerTest {

    private static final String PAYLOAD = "{\"id\":\"evt_test\",\"type\":\"invoice.paid\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SuscripcionService suscripcionService;

    @MockitoBean
    private StripeService stripeService;

    @MockitoBean
    private Katedra.Server.service.JwtService jwtService;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockitoBean
    private Katedra.Server.repository.UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("A valid signature is processed and acknowledged with 200")
    void firmaValidaSeProcesa() throws Exception {
        Event event = new Event();
        event.setId("evt_test");
        given(stripeService.verificarEvento(eq(PAYLOAD), eq("firma_valida"))).willReturn(event);

        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", "firma_valida")
                        .content(PAYLOAD))
                .andExpect(status().isOk());

        verify(suscripcionService).procesarEvento(event);
    }

    @Test
    @DisplayName("An invalid signature is rejected with 400 and never reaches the service")
    void firmaInvalidaSeRechaza() throws Exception {
        // The signature check is the only authentication this endpoint has: it is
        // permitAll in the security chain because Stripe sends no JWT.
        willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Firma inválida"))
                .given(stripeService).verificarEvento(anyString(), anyString());

        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", "firma_falsificada")
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest());

        verify(suscripcionService, never()).procesarEvento(any());
    }

    @Test
    @DisplayName("A failure while processing still returns 200 so Stripe does not retry-storm")
    void unFalloInternoSeAcusaDeRecibidoIgual() throws Exception {
        // Returning 5xx would make Stripe retry with backoff for days, turning a bug on our
        // side into a self-inflicted flood. The next sync reconciles state instead.
        Event event = new Event();
        event.setId("evt_test");
        given(stripeService.verificarEvento(anyString(), anyString())).willReturn(event);
        willThrow(new IllegalStateException("fallo inesperado"))
                .given(suscripcionService).procesarEvento(any());

        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", "firma_valida")
                        .content(PAYLOAD))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A request without the signature header is rejected")
    void sinCabeceraDeFirmaSeRechaza() throws Exception {
        mockMvc.perform(post("/webhooks/stripe").content(PAYLOAD))
                .andExpect(status().is4xxClientError());

        verify(suscripcionService, never()).procesarEvento(any());
    }
}
