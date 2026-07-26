package Katedra.Server.service;

import Katedra.Server.dto.ExportacionArchivoDTO;
import Katedra.Server.dto.MaterialExportableDTO;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.model.FormatoExportacion;
import Katedra.Server.model.PiezaMaterial;
import Katedra.Server.model.Temario;
import Katedra.Server.repository.ContenidoTemarioRepository;
import Katedra.Server.service.export.ExportadorMaterial;
import Katedra.Server.service.export.NombreArchivo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a piece of generated material into a downloadable file.
 *
 * <p>Validates the request, checks ownership, snapshots the content, and hands the snapshot to the
 * exporter registered for the requested format.
 */
@Service
public class ExportacionMaterialService {

    private static final Logger log = LoggerFactory.getLogger(ExportacionMaterialService.class);

    private final TemarioAccessGuard guard;
    private final ContenidoTemarioRepository contenidoTemarioRepository;
    private final Map<FormatoExportacion, ExportadorMaterial> exportadores = new EnumMap<>(FormatoExportacion.class);

    private final PlanLimitService planLimitService;

    public ExportacionMaterialService(TemarioAccessGuard guard,
                                      ContenidoTemarioRepository contenidoTemarioRepository,
                                      PlanLimitService planLimitService,
                                      List<ExportadorMaterial> exportadores) {
        this.guard = guard;
        this.contenidoTemarioRepository = contenidoTemarioRepository;
        this.planLimitService = planLimitService;
        exportadores.forEach(exportador -> this.exportadores.put(exportador.formato(), exportador));
    }

    /**
     * The whole flow runs inside the transaction on purpose: {@code Temario.asignatura} is LAZY, so
     * the snapshot must be built while the session is open.
     */
    @Transactional(readOnly = true)
    public ExportacionArchivoDTO exportar(String temarioId, String userEmail, String piezaSolicitada, String formatoSolicitado) {
        return exportar(temarioId, userEmail, piezaSolicitada, formatoSolicitado, "light");
    }

    /**
     * @param theme 'light' or 'dark' — mirrors the app's own theme toggle, so a slide export looks
     *              like a continuation of whatever the teacher was looking at, not a fixed style.
     */
    @Transactional(readOnly = true)
    public ExportacionArchivoDTO exportar(String temarioId, String userEmail, String piezaSolicitada, String formatoSolicitado, String theme) {
        long inicio = System.currentTimeMillis();

        PiezaMaterial pieza = parsearPieza(piezaSolicitada);
        FormatoExportacion formato = parsearFormato(formatoSolicitado);
        validarCombinacion(pieza, formato);

        Temario temario = guard.findOwnedTemario(temarioId, userEmail);

        // After the ownership check on purpose: a plan-based 403 raised earlier would
        // reveal whether someone else's temario exists.
        planLimitService.validarExportacion(temario.getUsuario(), pieza, formato);
        planLimitService.reservarExportacion(temario.getUsuario());

        ContenidoTemario contenido = contenidoTemarioRepository.findByTemarioId(temarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Este temario todavía no tiene material generado"));

        MaterialExportableDTO material = construirMaterial(temario, contenido, pieza, theme);
        byte[] bytes = renderizar(formato, material);

        String nombre = NombreArchivo.construir(material.materia(), material.temarioTitulo(), pieza, formato.getExtension());
        log.info("Exportación temario={} pieza={} formato={} bytes={} ms={}",
                temarioId, pieza.getValor(), formato.getValor(), bytes.length, System.currentTimeMillis() - inicio);

        return new ExportacionArchivoDTO(nombre, formato.getMediaType(), bytes);
    }

    private PiezaMaterial parsearPieza(String valor) {
        try {
            return PiezaMaterial.fromValor(valor);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pieza de material no válida: " + valor);
        }
    }

    private FormatoExportacion parsearFormato(String valor) {
        try {
            return FormatoExportacion.fromValor(valor);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato de exportación no válido: " + valor);
        }
    }

    private void validarCombinacion(PiezaMaterial pieza, FormatoExportacion formato) {
        if (!formato.soporta(pieza)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El formato " + formato.getValor() + " no está disponible para " + pieza.getValor());
        }
    }

    private MaterialExportableDTO construirMaterial(Temario temario, ContenidoTemario contenido, PiezaMaterial pieza, String theme) {
        exigirContenido(contenido, pieza);

        return new MaterialExportableDTO(
                temario.getAsignatura().getNombre(),
                temario.getTitulo(),
                temario.getGradoAcademico(),
                pieza,
                contenido.getTeoria(),
                contenido.getEvaluacion(),
                contenido.getDiapositivas(),
                theme == null || theme.isBlank() ? "light" : theme);
    }

    /** An empty piece is a 404, never an empty document. */
    private void exigirContenido(ContenidoTemario contenido, PiezaMaterial pieza) {
        boolean disponible = switch (pieza) {
            case TEORIA -> contenido.getTeoria() != null && !contenido.getTeoria().isBlank();
            case EVALUACION -> contenido.getEvaluacion() != null && !contenido.getEvaluacion().isEmpty();
            case DIAPOSITIVAS -> contenido.getDiapositivas() != null && !contenido.getDiapositivas().isEmpty();
            case ESTRUCTURA -> contenido.getEstructura() != null && !contenido.getEstructura().isBlank();
        };

        if (!disponible) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Todavía no has generado " + pieza.getValor() + " para este temario");
        }
    }

    private byte[] renderizar(FormatoExportacion formato, MaterialExportableDTO material) {
        ExportadorMaterial exportador = exportadores.get(formato);
        if (exportador == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato de exportación no disponible: " + formato.getValor());
        }
        try {
            return exportador.exportar(material);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            log.error("Fallo al generar la exportación {} de {}", formato.getValor(), material.pieza().getValor(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el archivo. Intenta de nuevo.");
        }
    }
}
