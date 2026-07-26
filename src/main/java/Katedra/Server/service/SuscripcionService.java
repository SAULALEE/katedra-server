package Katedra.Server.service;

import Katedra.Server.dto.IniciarSuscripcionRequestDTO;
import Katedra.Server.dto.IniciarSuscripcionResponseDTO;
import Katedra.Server.dto.SuscripcionResponseDTO;
import Katedra.Server.model.EstadoSuscripcion;
import Katedra.Server.model.PlanUsuario;
import Katedra.Server.model.Suscripcion;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.SuscripcionRepository;
import Katedra.Server.repository.UsuarioRepository;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Owns the subscription lifecycle and the local plan state.
 *
 * <p>The governing rule is that <b>the server never trusts a claim of payment</b> — not
 * from the browser, and not from a webhook payload either. Both paths only say "look
 * again"; {@link #sincronizarDesdeStripe} then re-reads live state from Stripe and writes
 * whatever it finds.
 */
@Service
public class SuscripcionService {

    private static final Logger log = LoggerFactory.getLogger(SuscripcionService.class);

    private final SuscripcionRepository suscripcionRepository;
    private final UsuarioRepository usuarioRepository;
    private final StripeService stripeService;

    public SuscripcionService(SuscripcionRepository suscripcionRepository,
                              UsuarioRepository usuarioRepository,
                              StripeService stripeService) {
        this.suscripcionRepository = suscripcionRepository;
        this.usuarioRepository = usuarioRepository;
        this.stripeService = stripeService;
    }

    /**
     * Step one of the checkout: registers billing details and creates an unpaid
     * subscription, returning the client secret the browser confirms.
     *
     * <p>Idempotent by design. If the user already has an INCOMPLETA subscription for the
     * same billing period, its billing details are refreshed and the <b>same</b> client
     * secret is returned. Without this, every "back" in the two-step checkout would spawn
     * another incomplete subscription in Stripe. Solving it here rather than with a flag in
     * the browser is deliberate: client-side state drifts, a UNIQUE-keyed server lookup
     * does not.
     */
    @Transactional
    public IniciarSuscripcionResponseDTO iniciar(String userEmail, IniciarSuscripcionRequestDTO request) {
        Usuario usuario = buscarUsuario(userEmail);

        if (usuario.getPlan() == PlanUsuario.PRO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya tienes el plan Pro activo.");
        }

        String publishableKey = stripeService.getPublishableKey();
        String priceId = stripeService.resolverPriceId(request.ciclo());

        Optional<Suscripcion> enCurso = suscripcionRepository
                .findFirstByUsuarioIdAndEstadoOrderByCreatedAtDesc(usuario.getId(), EstadoSuscripcion.INCOMPLETA)
                .filter(s -> s.getCiclo() == request.ciclo());

        if (enCurso.isPresent()) {
            Suscripcion suscripcion = enCurso.get();
            Subscription stripeSub = stripeService.recuperarSuscripcion(suscripcion.getStripeSubscriptionId());
            if ("incomplete".equals(stripeSub.getStatus())) {
                stripeService.asegurarCustomer(usuario, request.facturacion(), suscripcion.getStripeCustomerId());
                return new IniciarSuscripcionResponseDTO(
                        suscripcion.getId(),
                        stripeService.extraerClientSecret(stripeSub),
                        publishableKey,
                        suscripcion.getCiclo());
            }
        }

        String customerId = stripeService.asegurarCustomer(usuario, request.facturacion(), customerIdPrevio(usuario));
        Subscription stripeSub = stripeService.crearSuscripcionIncompleta(customerId, priceId, usuario.getId());

        Suscripcion suscripcion = new Suscripcion(
                usuario.getId(), request.ciclo(), customerId, stripeSub.getId(), priceId);
        suscripcion.setEstado(EstadoSuscripcion.fromStripe(stripeSub.getStatus()));
        Suscripcion guardada = suscripcionRepository.save(suscripcion);

        return new IniciarSuscripcionResponseDTO(
                guardada.getId(),
                stripeService.extraerClientSecret(stripeSub),
                publishableKey,
                guardada.getCiclo());
    }

    /**
     * The single convergence point for both activation paths.
     *
     * <p>Re-reads the subscription from Stripe and assigns local state from what it finds.
     * Two properties follow, and both are the reason this method must never be
     * "optimized" into reading {@code event.getDataObjectDeserializer()}:
     *
     * <ul>
     *   <li><b>Idempotent.</b> Every write is a state assignment, never an increment, and
     *       the row is located by the UNIQUE stripe_subscription_id. Running this once or
     *       fifty times produces the same row, so the webhook and the client confirmation
     *       both firing cannot double-grant.
     *   <li><b>Order-independent.</b> Because the live state is re-read, an
     *       {@code invoice.paid} arriving after a {@code customer.subscription.updated}
     *       still converges on the truth. Trusting payloads would make the last-delivered
     *       event win instead of the most recent one.
     * </ul>
     */
    @Transactional
    public SuscripcionResponseDTO sincronizarDesdeStripe(String stripeSubscriptionId) {
        Subscription stripeSub = stripeService.recuperarSuscripcion(stripeSubscriptionId);

        Suscripcion suscripcion = suscripcionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Suscripción no encontrada"));

        EstadoSuscripcion estado = EstadoSuscripcion.fromStripe(stripeSub.getStatus());
        LocalDateTime[] periodo = stripeService.periodoDe(stripeSub);

        suscripcion.setEstado(estado);
        suscripcion.setPeriodoInicio(periodo[0]);
        suscripcion.setPeriodoFin(periodo[1]);
        suscripcion.setCancelaAlFinal(Boolean.TRUE.equals(stripeSub.getCancelAtPeriodEnd()));
        if (estado == EstadoSuscripcion.CANCELADA && suscripcion.getCanceladaEn() == null) {
            suscripcion.setCanceladaEn(LocalDateTime.now());
        }
        suscripcion.setUpdatedAt(LocalDateTime.now());
        suscripcionRepository.save(suscripcion);

        Usuario usuario = usuarioRepository.findById(suscripcion.getUsuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        usuario.setPlan(estado.esVigente() ? PlanUsuario.PRO : PlanUsuario.FREE);
        usuario.setUpdatedAt(LocalDateTime.now());
        usuarioRepository.save(usuario);

        log.info("Suscripción {} sincronizada: estado={} plan={}",
                stripeSubscriptionId, estado, usuario.getPlan());

        return mapToDTO(suscripcion, usuario.getPlan());
    }

    /**
     * Called by the browser once Stripe reports the payment succeeded. Works with no
     * webhook infrastructure at all, which is what makes the feature demoable on a laptop.
     *
     * <p>The client's claim is not believed: it only identifies which subscription to
     * re-read. Ownership is checked first so one user cannot trigger a sync on another's
     * subscription.
     */
    @Transactional
    public SuscripcionResponseDTO confirmar(String userEmail, String suscripcionId) {
        Usuario usuario = buscarUsuario(userEmail);
        Suscripcion suscripcion = suscripcionRepository.findById(suscripcionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suscripción no encontrada"));

        if (!suscripcion.getUsuarioId().equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado a esta suscripción");
        }

        return sincronizarDesdeStripe(suscripcion.getStripeSubscriptionId());
    }

    /** Current billing state, or a FREE placeholder for a user who never subscribed. */
    @Transactional(readOnly = true)
    public SuscripcionResponseDTO miSuscripcion(String userEmail) {
        Usuario usuario = buscarUsuario(userEmail);

        return suscripcionRepository.findFirstByUsuarioIdOrderByCreatedAtDesc(usuario.getId())
                .map(s -> mapToDTO(s, usuario.getPlan()))
                .orElseGet(() -> new SuscripcionResponseDTO(
                        null, PlanUsuario.FREE, null, null, null, null, false));
    }

    /**
     * Cancels at the end of the paid period. The plan stays PRO until Stripe actually ends
     * the subscription and the resulting webhook downgrades it — the user paid for this
     * period.
     */
    @Transactional
    public SuscripcionResponseDTO cancelar(String userEmail) {
        Usuario usuario = buscarUsuario(userEmail);
        Suscripcion suscripcion = suscripcionRepository
                .findFirstByUsuarioIdAndEstadoOrderByCreatedAtDesc(usuario.getId(), EstadoSuscripcion.ACTIVA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No tienes una suscripción activa que cancelar."));

        stripeService.cancelarAlFinalDelPeriodo(suscripcion.getStripeSubscriptionId());
        return sincronizarDesdeStripe(suscripcion.getStripeSubscriptionId());
    }

    /**
     * Routes a verified Stripe event to the sync.
     *
     * <p>Only the subscription id is taken from the payload — never the status. Anything
     * else is acknowledged and ignored: returning an error for an event we do not handle
     * would make Stripe retry it indefinitely.
     */
    @Transactional
    public void procesarEvento(Event event) {
        String tipo = event.getType();

        switch (tipo) {
            case "invoice.paid", "invoice.payment_failed",
                 "customer.subscription.updated", "customer.subscription.deleted" -> {
                String subscriptionId = extraerSubscriptionId(event);
                if (subscriptionId == null) {
                    log.warn("Evento {} sin subscription id, se ignora", tipo);
                    return;
                }
                sincronizarDesdeStripe(subscriptionId);
            }
            default -> log.debug("Evento de Stripe ignorado: {}", tipo);
        }
    }

    private String extraerSubscriptionId(Event event) {
        return event.getDataObjectDeserializer().getObject()
                .map(objeto -> {
                    if (objeto instanceof Subscription subscription) {
                        return subscription.getId();
                    }
                    if (objeto instanceof com.stripe.model.Invoice invoice) {
                        return invoice.getParent() != null
                                && invoice.getParent().getSubscriptionDetails() != null
                                ? invoice.getParent().getSubscriptionDetails().getSubscription()
                                : null;
                    }
                    return null;
                })
                .orElse(null);
    }

    private String customerIdPrevio(Usuario usuario) {
        return suscripcionRepository.findFirstByUsuarioIdOrderByCreatedAtDesc(usuario.getId())
                .map(Suscripcion::getStripeCustomerId)
                .orElse(null);
    }

    private Usuario buscarUsuario(String userEmail) {
        return usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private SuscripcionResponseDTO mapToDTO(Suscripcion suscripcion, PlanUsuario plan) {
        return new SuscripcionResponseDTO(
                suscripcion.getId(),
                plan,
                suscripcion.getEstado(),
                suscripcion.getCiclo(),
                suscripcion.getPeriodoInicio(),
                suscripcion.getPeriodoFin(),
                suscripcion.isCancelaAlFinal());
    }
}
