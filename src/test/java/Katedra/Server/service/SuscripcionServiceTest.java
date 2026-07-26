package Katedra.Server.service;

import Katedra.Server.dto.DatosFacturacionDTO;
import Katedra.Server.dto.IniciarSuscripcionRequestDTO;
import Katedra.Server.dto.IniciarSuscripcionResponseDTO;
import Katedra.Server.dto.SuscripcionResponseDTO;
import Katedra.Server.model.CicloFacturacion;
import Katedra.Server.model.EstadoSuscripcion;
import Katedra.Server.model.PlanUsuario;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Suscripcion;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.SuscripcionRepository;
import Katedra.Server.repository.UsuarioRepository;
import com.stripe.model.Subscription;
import com.stripe.model.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SuscripcionServiceTest {

    private static final String STRIPE_SUB_ID = "sub_test_123";
    private static final String STRIPE_CUSTOMER_ID = "cus_test_123";
    private static final String EMAIL = "profesor@katedra.com";

    @Mock
    private SuscripcionRepository suscripcionRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private StripeService stripeService;

    @InjectMocks
    private SuscripcionService suscripcionService;

    private Usuario usuario;
    private Suscripcion suscripcion;

    @BeforeEach
    void setUp() {
        usuario = new Usuario(EMAIL, "hash", "Docente", RolUsuario.ROLE_PROFESOR);
        ReflectionTestUtils.setField(usuario, "id", "user-1");
        usuario.setPlan(PlanUsuario.FREE);

        suscripcion = new Suscripcion(
                "user-1", CicloFacturacion.MENSUAL, STRIPE_CUSTOMER_ID, STRIPE_SUB_ID, "price_mensual");
        ReflectionTestUtils.setField(suscripcion, "id", "susc-1");
    }

    private Subscription stripeSubConEstado(String status) {
        Subscription stripeSub = new Subscription();
        stripeSub.setId(STRIPE_SUB_ID);
        stripeSub.setStatus(status);
        return stripeSub;
    }

    private void dadoQueStripeReporta(String status) {
        given(stripeService.recuperarSuscripcion(STRIPE_SUB_ID)).willReturn(stripeSubConEstado(status));
        given(stripeService.periodoDe(any())).willReturn(new LocalDateTime[]{
                LocalDateTime.of(2026, 7, 25, 0, 0), LocalDateTime.of(2026, 8, 25, 0, 0)});
        given(suscripcionRepository.findByStripeSubscriptionId(STRIPE_SUB_ID)).willReturn(Optional.of(suscripcion));
        given(usuarioRepository.findById("user-1")).willReturn(Optional.of(usuario));
    }

    // --- sincronizarDesdeStripe: the convergence point ---

    @Test
    @DisplayName("An active Stripe subscription grants PRO")
    void stripeActivaOtorgaPro() {
        dadoQueStripeReporta("active");

        SuscripcionResponseDTO respuesta = suscripcionService.sincronizarDesdeStripe(STRIPE_SUB_ID);

        assertThat(usuario.getPlan()).isEqualTo(PlanUsuario.PRO);
        assertThat(suscripcion.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(respuesta.plan()).isEqualTo(PlanUsuario.PRO);
        assertThat(respuesta.periodoFin()).isEqualTo(LocalDateTime.of(2026, 8, 25, 0, 0));
    }

    @Test
    @DisplayName("A canceled Stripe subscription revokes PRO")
    void stripeCanceladaRevocaPro() {
        usuario.setPlan(PlanUsuario.PRO);
        dadoQueStripeReporta("canceled");

        suscripcionService.sincronizarDesdeStripe(STRIPE_SUB_ID);

        assertThat(usuario.getPlan()).isEqualTo(PlanUsuario.FREE);
        assertThat(suscripcion.getEstado()).isEqualTo(EstadoSuscripcion.CANCELADA);
        assertThat(suscripcion.getCanceladaEn()).isNotNull();
    }

    @Test
    @DisplayName("A failed renewal revokes PRO without ending the subscription")
    void stripeImpagaRevocaPro() {
        usuario.setPlan(PlanUsuario.PRO);
        dadoQueStripeReporta("past_due");

        suscripcionService.sincronizarDesdeStripe(STRIPE_SUB_ID);

        assertThat(usuario.getPlan()).isEqualTo(PlanUsuario.FREE);
        assertThat(suscripcion.getEstado()).isEqualTo(EstadoSuscripcion.IMPAGA);
        assertThat(suscripcion.getCanceladaEn()).isNull();
    }

    @Test
    @DisplayName("Syncing twice for the same payment leaves the same state — no double grant")
    void sincronizarDosVecesEsIdempotente() {
        // This is the property that lets the webhook and the client confirmation both fire
        // for one payment. Every write is a state assignment, never an increment.
        dadoQueStripeReporta("active");

        suscripcionService.sincronizarDesdeStripe(STRIPE_SUB_ID);
        suscripcionService.sincronizarDesdeStripe(STRIPE_SUB_ID);

        assertThat(usuario.getPlan()).isEqualTo(PlanUsuario.PRO);
        assertThat(suscripcion.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
    }

    @Test
    @DisplayName("State comes from the live Stripe read, never from stale local state")
    void elEstadoSeLeeSiempreDeStripe() {
        // Local state says ACTIVA, Stripe says canceled. Stripe must win — otherwise a
        // cancellation processed elsewhere would never revoke access.
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        usuario.setPlan(PlanUsuario.PRO);
        dadoQueStripeReporta("canceled");

        suscripcionService.sincronizarDesdeStripe(STRIPE_SUB_ID);

        verify(stripeService).recuperarSuscripcion(STRIPE_SUB_ID);
        assertThat(usuario.getPlan()).isEqualTo(PlanUsuario.FREE);
    }

    @Test
    @DisplayName("An unknown Stripe status fails closed rather than granting PRO")
    void estadoDesconocidoNoOtorgaPro() {
        dadoQueStripeReporta("un_estado_que_stripe_agregue_manana");

        suscripcionService.sincronizarDesdeStripe(STRIPE_SUB_ID);

        assertThat(usuario.getPlan()).isEqualTo(PlanUsuario.FREE);
    }

    // --- iniciar: idempotency against the checkout back button ---

    @Test
    @DisplayName("Starting a checkout with an in-flight one reuses it instead of creating a second Stripe subscription")
    void iniciarReutilizaLaSuscripcionIncompleta() {
        // Without this, every "Volver" in the two-step checkout would leave another
        // incomplete subscription behind in Stripe.
        suscripcion.setEstado(EstadoSuscripcion.INCOMPLETA);
        given(usuarioRepository.findByEmail(EMAIL)).willReturn(Optional.of(usuario));
        given(suscripcionRepository.findFirstByUsuarioIdAndEstadoOrderByCreatedAtDesc(
                "user-1", EstadoSuscripcion.INCOMPLETA)).willReturn(Optional.of(suscripcion));
        given(stripeService.recuperarSuscripcion(STRIPE_SUB_ID)).willReturn(stripeSubConEstado("incomplete"));
        given(stripeService.extraerClientSecret(any())).willReturn("pi_secret_reutilizado");

        IniciarSuscripcionResponseDTO respuesta = suscripcionService.iniciar(EMAIL, peticionMensual());

        assertThat(respuesta.suscripcionId()).isEqualTo("susc-1");
        assertThat(respuesta.clientSecret()).isEqualTo("pi_secret_reutilizado");
        verify(stripeService, never()).crearSuscripcionIncompleta(anyString(), anyString(), anyString());
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("An expired incomplete subscription is replaced because Stripe cannot confirm it anymore")
    void iniciarReemplazaLaSuscripcionIncompletaExpirada() {
        suscripcion.setEstado(EstadoSuscripcion.INCOMPLETA);
        given(usuarioRepository.findByEmail(EMAIL)).willReturn(Optional.of(usuario));
        given(suscripcionRepository.findFirstByUsuarioIdAndEstadoOrderByCreatedAtDesc(
                "user-1", EstadoSuscripcion.INCOMPLETA)).willReturn(Optional.of(suscripcion));
        given(stripeService.recuperarSuscripcion(STRIPE_SUB_ID))
                .willReturn(stripeSubConEstado("incomplete_expired"));
        given(suscripcionRepository.findFirstByUsuarioIdOrderByCreatedAtDesc("user-1"))
                .willReturn(Optional.of(suscripcion));
        given(stripeService.asegurarCustomer(any(), any(), any())).willReturn(STRIPE_CUSTOMER_ID);
        given(stripeService.resolverPriceId(CicloFacturacion.MENSUAL)).willReturn("price_mensual");
        given(stripeService.crearSuscripcionIncompleta(any(), any(), any()))
                .willReturn(stripeSubConEstado("incomplete"));
        given(stripeService.extraerClientSecret(any())).willReturn("pi_secret_nuevo");
        given(suscripcionRepository.save(any())).willAnswer(i -> i.getArgument(0));

        IniciarSuscripcionResponseDTO respuesta = suscripcionService.iniciar(EMAIL, peticionMensual());

        assertThat(respuesta.clientSecret()).isEqualTo("pi_secret_nuevo");
        verify(stripeService).crearSuscripcionIncompleta(
                STRIPE_CUSTOMER_ID, "price_mensual", "user-1");
    }

    @Test
    @DisplayName("Webhook processing owns a transaction so subscription and user update atomically")
    void procesarEventoEsTransaccional() throws NoSuchMethodException {
        Method method = SuscripcionService.class.getMethod("procesarEvento", Event.class);

        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    @DisplayName("Switching billing period starts a fresh subscription instead of reusing the other one")
    void iniciarConOtroCicloNoReutiliza() {
        suscripcion.setEstado(EstadoSuscripcion.INCOMPLETA);
        given(usuarioRepository.findByEmail(EMAIL)).willReturn(Optional.of(usuario));
        given(suscripcionRepository.findFirstByUsuarioIdAndEstadoOrderByCreatedAtDesc(
                "user-1", EstadoSuscripcion.INCOMPLETA)).willReturn(Optional.of(suscripcion));
        given(suscripcionRepository.findFirstByUsuarioIdOrderByCreatedAtDesc("user-1"))
                .willReturn(Optional.of(suscripcion));
        given(stripeService.asegurarCustomer(any(), any(), any())).willReturn(STRIPE_CUSTOMER_ID);
        given(stripeService.resolverPriceId(CicloFacturacion.ANUAL)).willReturn("price_anual");
        given(stripeService.crearSuscripcionIncompleta(any(), any(), any()))
                .willReturn(stripeSubConEstado("incomplete"));
        given(stripeService.extraerClientSecret(any())).willReturn("pi_secret_nuevo");
        given(suscripcionRepository.save(any())).willAnswer(i -> i.getArgument(0));

        suscripcionService.iniciar(EMAIL, new IniciarSuscripcionRequestDTO(CicloFacturacion.ANUAL, facturacion()));

        verify(stripeService).crearSuscripcionIncompleta(any(), any(), any());
    }

    @Test
    @DisplayName("Reuses the existing Stripe customer so payment history is not split")
    void iniciarReutilizaElCustomerDeStripe() {
        given(usuarioRepository.findByEmail(EMAIL)).willReturn(Optional.of(usuario));
        given(suscripcionRepository.findFirstByUsuarioIdAndEstadoOrderByCreatedAtDesc(
                "user-1", EstadoSuscripcion.INCOMPLETA)).willReturn(Optional.empty());
        given(suscripcionRepository.findFirstByUsuarioIdOrderByCreatedAtDesc("user-1"))
                .willReturn(Optional.of(suscripcion));
        given(stripeService.asegurarCustomer(any(), any(), any())).willReturn(STRIPE_CUSTOMER_ID);
        given(stripeService.resolverPriceId(any())).willReturn("price_mensual");
        given(stripeService.crearSuscripcionIncompleta(any(), any(), any()))
                .willReturn(stripeSubConEstado("incomplete"));
        given(stripeService.extraerClientSecret(any())).willReturn("pi_secret");
        given(suscripcionRepository.save(any())).willAnswer(i -> i.getArgument(0));

        suscripcionService.iniciar(EMAIL, peticionMensual());

        verify(stripeService).asegurarCustomer(usuario, facturacion(), STRIPE_CUSTOMER_ID);
    }

    @Test
    @DisplayName("A user who is already PRO cannot start a second checkout")
    void iniciarRechazaSiYaEsPro() {
        usuario.setPlan(PlanUsuario.PRO);
        given(usuarioRepository.findByEmail(EMAIL)).willReturn(Optional.of(usuario));

        ResponseStatusException ex = (ResponseStatusException)
                catchThrowable(() -> suscripcionService.iniciar(EMAIL, peticionMensual()));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(stripeService, never()).crearSuscripcionIncompleta(anyString(), anyString(), anyString());
    }

    // --- confirmar: ownership ---

    @Test
    @DisplayName("Confirming someone else's subscription is forbidden")
    void confirmarSuscripcionAjenaEsProhibido() {
        Suscripcion ajena = new Suscripcion(
                "otro-user", CicloFacturacion.MENSUAL, "cus_otro", "sub_otro", "price_mensual");
        given(usuarioRepository.findByEmail(EMAIL)).willReturn(Optional.of(usuario));
        given(suscripcionRepository.findById("susc-ajena")).willReturn(Optional.of(ajena));

        ResponseStatusException ex = (ResponseStatusException)
                catchThrowable(() -> suscripcionService.confirmar(EMAIL, "susc-ajena"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(stripeService, never()).recuperarSuscripcion(anyString());
    }

    @Test
    @DisplayName("Confirming re-reads from Stripe rather than believing the client")
    void confirmarReleeDeStripe() {
        given(usuarioRepository.findByEmail(EMAIL)).willReturn(Optional.of(usuario));
        given(suscripcionRepository.findById("susc-1")).willReturn(Optional.of(suscripcion));
        dadoQueStripeReporta("active");

        SuscripcionResponseDTO respuesta = suscripcionService.confirmar(EMAIL, "susc-1");

        verify(stripeService).recuperarSuscripcion(STRIPE_SUB_ID);
        assertThat(respuesta.plan()).isEqualTo(PlanUsuario.PRO);
    }

    @Test
    @DisplayName("A client claiming success when Stripe says otherwise does not get PRO")
    void confirmarNoOtorgaSiStripeNoConfirma() {
        given(usuarioRepository.findByEmail(EMAIL)).willReturn(Optional.of(usuario));
        given(suscripcionRepository.findById("susc-1")).willReturn(Optional.of(suscripcion));
        dadoQueStripeReporta("incomplete");

        suscripcionService.confirmar(EMAIL, "susc-1");

        assertThat(usuario.getPlan()).isEqualTo(PlanUsuario.FREE);
    }

    // --- miSuscripcion / cancelar ---

    @Test
    @DisplayName("A user who never subscribed reports FREE instead of 404")
    void miSuscripcionSinHistorialDevuelveFree() {
        given(usuarioRepository.findByEmail(EMAIL)).willReturn(Optional.of(usuario));
        given(suscripcionRepository.findFirstByUsuarioIdOrderByCreatedAtDesc("user-1"))
                .willReturn(Optional.empty());

        SuscripcionResponseDTO respuesta = suscripcionService.miSuscripcion(EMAIL);

        assertThat(respuesta.plan()).isEqualTo(PlanUsuario.FREE);
        assertThat(respuesta.id()).isNull();
    }

    @Test
    @DisplayName("Cancelling schedules the end of period and keeps PRO until then")
    void cancelarProgramaElFinDePeriodoYMantienePro() {
        // The user paid for this period; access ends when the period does, not now.
        usuario.setPlan(PlanUsuario.PRO);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        given(usuarioRepository.findByEmail(EMAIL)).willReturn(Optional.of(usuario));
        given(suscripcionRepository.findFirstByUsuarioIdAndEstadoOrderByCreatedAtDesc(
                "user-1", EstadoSuscripcion.ACTIVA)).willReturn(Optional.of(suscripcion));

        Subscription stripeSub = stripeSubConEstado("active");
        stripeSub.setCancelAtPeriodEnd(true);
        given(stripeService.recuperarSuscripcion(STRIPE_SUB_ID)).willReturn(stripeSub);
        given(stripeService.periodoDe(any())).willReturn(new LocalDateTime[]{
                LocalDateTime.of(2026, 7, 25, 0, 0), LocalDateTime.of(2026, 8, 25, 0, 0)});
        given(suscripcionRepository.findByStripeSubscriptionId(STRIPE_SUB_ID)).willReturn(Optional.of(suscripcion));
        given(usuarioRepository.findById("user-1")).willReturn(Optional.of(usuario));

        SuscripcionResponseDTO respuesta = suscripcionService.cancelar(EMAIL);

        verify(stripeService).cancelarAlFinalDelPeriodo(STRIPE_SUB_ID);
        assertThat(usuario.getPlan()).isEqualTo(PlanUsuario.PRO);
        assertThat(respuesta.cancelaAlFinal()).isTrue();
        assertThat(respuesta.periodoFin()).isEqualTo(LocalDateTime.of(2026, 8, 25, 0, 0));
    }

    @Test
    @DisplayName("Cancelling without an active subscription is a 404, not a Stripe call")
    void cancelarSinSuscripcionActiva() {
        given(usuarioRepository.findByEmail(EMAIL)).willReturn(Optional.of(usuario));
        given(suscripcionRepository.findFirstByUsuarioIdAndEstadoOrderByCreatedAtDesc(
                "user-1", EstadoSuscripcion.ACTIVA)).willReturn(Optional.empty());

        ResponseStatusException ex = (ResponseStatusException)
                catchThrowable(() -> suscripcionService.cancelar(EMAIL));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(stripeService, never()).cancelarAlFinalDelPeriodo(anyString());
    }

    private IniciarSuscripcionRequestDTO peticionMensual() {
        return new IniciarSuscripcionRequestDTO(CicloFacturacion.MENSUAL, facturacion());
    }

    private DatosFacturacionDTO facturacion() {
        return new DatosFacturacionDTO("Docente Katedra", "facturacion@katedra.com", "MX", "CDMX", "Calle 1", "01000");
    }
}
