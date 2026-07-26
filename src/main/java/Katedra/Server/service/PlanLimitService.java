package Katedra.Server.service;

import Katedra.Server.dto.UsoPlanResponseDTO;
import Katedra.Server.model.FormatoExportacion;
import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.OrigenTemario;
import Katedra.Server.model.PiezaMaterial;
import Katedra.Server.model.PlanUsuario;
import Katedra.Server.model.UsoDiario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsoDiarioRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * The single gate for every Free/Pro restriction. All three choke points — AI generation,
 * syllabus ingestion and material export — call into this class rather than reading
 * {@code usuario.getPlan()} themselves, so the rules live in exactly one file.
 *
 * <p><b>The plan is always read from the database, never from the JWT.</b> The token is
 * signed and cannot be forged, but it lives an hour and the plan changes mid-session at
 * checkout. A gate reading the claim would keep granting PRO for up to an hour after a
 * cancellation, and keep denying it for up to an hour after a purchase.
 *
 * <p>Two kinds of rejection, deliberately different status codes: a capability the tier
 * does not include is 403 (upgrading is the only fix), while an exhausted daily quota is
 * 429 (waiting also fixes it). The frontend keys its upsell prompt off that distinction.
 */
@Service
public class PlanLimitService {

    private final UsuarioRepository usuarioRepository;
    private final UsoDiarioRepository usoDiarioRepository;

    public PlanLimitService(UsuarioRepository usuarioRepository, UsoDiarioRepository usoDiarioRepository) {
        this.usuarioRepository = usuarioRepository;
        this.usoDiarioRepository = usoDiarioRepository;
    }

    /**
     * Rejects a generation whose requested model or pieces exceed what the tier includes.
     * Called before any quota is reserved: a request that is not allowed at all should not
     * consume the user's daily allowance.
     */
    public void validarGeneracion(Usuario usuario, ModeloIA modelo, Set<PiezaMaterial> piezas) {
        PlanUsuario plan = planDe(usuario);

        if (modelo == ModeloIA.PRO && !plan.permiteModeloPro()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El modelo Catedrático es exclusivo del plan Pro. Mejora tu plan para usarlo.");
        }

        if (piezas != null && piezas.contains(PiezaMaterial.DIAPOSITIVAS) && !plan.permiteDiapositivas()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Generar diapositivas es exclusivo del plan Pro. Mejora tu plan para usarlas.");
        }
    }

    /** Rejects creating a syllabus from a file or a URL on a tier limited to manual entry. */
    public void validarOrigenTemario(Usuario usuario, OrigenTemario origen) {
        PlanUsuario plan = planDe(usuario);

        boolean permitido = switch (origen) {
            case MANUAL -> true;
            case ARCHIVO -> plan.permiteCargaArchivo();
            case URL -> plan.permiteCargaUrl();
        };

        if (!permitido) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Crear un temario desde " + origen.getEtiqueta() + " es exclusivo del plan Pro. "
                            + "En el plan Gratis puedes crearlo manualmente.");
        }
    }

    /**
     * Rejects an export format the tier does not include.
     *
     * <p>PPTX is gated by {@link PlanUsuario#permiteDiapositivas()} since it exists solely
     * for DIAPOSITIVAS, which FREE cannot generate in the first place. MARKDOWN and
     * APPS_SCRIPT (Google Forms) are gated separately by
     * {@link PlanUsuario#permiteExportacionAvanzada()}: FREE keeps PDF/DOCX so a syllabus
     * is always usable, but the more workflow-oriented formats are a Pro upsell.
     */
    public void validarExportacion(Usuario usuario, PiezaMaterial pieza, FormatoExportacion formato) {
        PlanUsuario plan = planDe(usuario);

        if (formato == FormatoExportacion.PPTX && !plan.permiteDiapositivas()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Exportar a PPTX es exclusivo del plan Pro. Mejora tu plan para usarlo.");
        }

        if ((formato == FormatoExportacion.MARKDOWN || formato == FormatoExportacion.APPS_SCRIPT)
                && !plan.permiteExportacionAvanzada()) {
            String nombreFormato = formato == FormatoExportacion.APPS_SCRIPT ? "Google Forms" : "Markdown";
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Exportar a " + nombreFormato + " es exclusivo del plan Pro. Mejora tu plan para usarlo.");
        }
    }

    /**
     * Reserves {@code cantidad} AI pieces against today's allowance, or throws 429.
     *
     * <p>{@code REQUIRES_NEW} is deliberate. The reservation must commit independently of
     * the caller's transaction, otherwise a later failure in that transaction would roll
     * the reservation back invisibly and the accounting would drift. The consequence is
     * that the only way to give quota back is the explicit {@link #liberarGeneraciones}
     * call — one rule, no hidden refunds.
     *
     * <p>Must be invoked synchronously, on the request thread. Reserving from inside the
     * generation's async completion stage would gate nothing: N concurrent requests would
     * all pass a check against a counter none of them had written yet.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reservarGeneraciones(Usuario usuario, int cantidad) {
        PlanUsuario plan = planDe(usuario);
        int limite = plan.getGeneracionesPorDia();

        if (cantidad < 1) {
            return;
        }
        if (cantidad > limite) {
            // Caught before touching SQL: the conditional UPDATE could never match, and a
            // bare 429 would not explain why the request is impossible rather than early.
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Esta generación necesita " + cantidad + " créditos y tu plan "
                            + plan.getEtiqueta() + " permite " + limite + " al día.");
        }

        LocalDate hoy = LocalDate.now();
        asegurarFila(usuario.getId(), hoy);

        if (usoDiarioRepository.consumirGeneraciones(usuario.getId(), hoy, cantidad, limite) == 0) {
            int usadas = generacionesUsadas(usuario.getId(), hoy);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Alcanzaste el límite diario de tu plan " + plan.getEtiqueta() + " ("
                            + usadas + "/" + limite + " generaciones). Mejora a Pro o vuelve mañana.");
        }
    }

    /**
     * Returns quota reserved for pieces that ultimately failed, so a provider outage does
     * not silently cost the user their day.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void liberarGeneraciones(Usuario usuario, int cantidad) {
        if (cantidad < 1) {
            return;
        }
        usoDiarioRepository.liberarGeneraciones(usuario.getId(), LocalDate.now(), cantidad);
    }

    /** Reserves one export against today's allowance, or throws 429. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reservarExportacion(Usuario usuario) {
        PlanUsuario plan = planDe(usuario);
        int limite = plan.getExportacionesPorDia();
        LocalDate hoy = LocalDate.now();

        asegurarFila(usuario.getId(), hoy);

        if (usoDiarioRepository.consumirExportaciones(usuario.getId(), hoy, 1, limite) == 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Alcanzaste el límite diario de exportaciones de tu plan " + plan.getEtiqueta()
                            + " (" + limite + " al día). Mejora a Pro o vuelve mañana.");
        }
    }

    /** Snapshot of today's usage and the tier's capabilities, for the sidebar meter. */
    @Transactional(readOnly = true)
    public UsoPlanResponseDTO consultarUso(String userEmail) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        PlanUsuario plan = planDe(usuario);
        LocalDate hoy = LocalDate.now();
        UsoDiario uso = usoDiarioRepository.findByUsuarioIdAndFecha(usuario.getId(), hoy).orElse(null);

        return new UsoPlanResponseDTO(
                plan,
                hoy,
                uso == null ? 0 : uso.getGeneraciones(),
                plan.getGeneracionesPorDia(),
                uso == null ? 0 : uso.getExportaciones(),
                plan.getExportacionesPorDia(),
                plan.permiteModeloPro(),
                plan.permiteDiapositivas(),
                plan.permiteCargaArchivo(),
                plan.permiteCargaUrl(),
                plan.permiteExportacionAvanzada());
    }

    /** Null-safe: a user row written before V23 could in principle carry no plan. */
    private PlanUsuario planDe(Usuario usuario) {
        return usuario.getPlan() == null ? PlanUsuario.FREE : usuario.getPlan();
    }

    private void asegurarFila(String usuarioId, LocalDate fecha) {
        usoDiarioRepository.crearFilaSiNoExiste(UUID.randomUUID().toString(), usuarioId, fecha);
    }

    private int generacionesUsadas(String usuarioId, LocalDate fecha) {
        return usoDiarioRepository.findByUsuarioIdAndFecha(usuarioId, fecha)
                .map(UsoDiario::getGeneraciones)
                .orElse(0);
    }
}
