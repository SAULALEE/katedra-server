package Katedra.Server.service;

import Katedra.Server.dto.ContenidoTemarioResponseDTO;
import Katedra.Server.dto.ContenidoFuenteResponseDTO;
import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.dto.GenerarMaterialRequestDTO;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.NivelAcademico;
import Katedra.Server.model.PiezaMaterial;
import Katedra.Server.model.Temario;
import Katedra.Server.repository.ContenidoTemarioRepository;
import Katedra.Server.repository.TemarioRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
public class ContenidoTemarioService {

    private static final Logger log = LoggerFactory.getLogger(ContenidoTemarioService.class);

    private final ContenidoTemarioRepository contenidoTemarioRepository;
    private final TemarioRepository temarioRepository;
    private final AiContentGeneratorService aiContentGeneratorService;
    private final UsuarioRepository usuarioRepository;

    public ContenidoTemarioService(
            ContenidoTemarioRepository contenidoTemarioRepository,
            TemarioRepository temarioRepository,
            AiContentGeneratorService aiContentGeneratorService,
            UsuarioRepository usuarioRepository) {
        this.contenidoTemarioRepository = contenidoTemarioRepository;
        this.temarioRepository = temarioRepository;
        this.aiContentGeneratorService = aiContentGeneratorService;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public ContenidoTemarioResponseDTO getContenidoByTemarioId(String temarioId, String userEmail) {
        findOwnedTemario(temarioId, userEmail);

        ContenidoTemario entity = contenidoTemarioRepository.findByTemarioId(temarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contenido no generado. Use POST /generar-material"));

        return mapToDTO(entity, Map.of());
    }

    @Transactional(readOnly = true)
    public ContenidoFuenteResponseDTO getFuenteByTemarioId(String temarioId, String userEmail) {
        Temario temario = findOwnedTemario(temarioId, userEmail);
        ContenidoTemario entity = contenidoTemarioRepository.findByTemarioId(temarioId)
                .orElse(null);
        String fuente = entity != null ? entity.getContenidoFuente() : null;
        if (fuente == null || fuente.isBlank()) {
            fuente = entity != null ? entity.getEstructura() : null;
        }
        if (fuente == null || fuente.isBlank()) {
            fuente = entity != null ? entity.getTeoria() : null;
        }
        if (fuente == null || fuente.isBlank()) {
            fuente = temario.getDescripcion();
        }
        if (fuente == null || fuente.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contenido fuente no disponible");
        }
        return new ContenidoFuenteResponseDTO(temarioId, fuente);
    }

    /**
     * Generates every requested piece, always overwriting any existing content for it.
     */
    @Transactional
    public CompletableFuture<ContenidoTemarioResponseDTO> generarMaterial(
            String temarioId, String userEmail, GenerarMaterialRequestDTO request) {

        Temario temario = findOwnedTemario(temarioId, userEmail);

        if (request == null || request.piezas() == null || request.piezas().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar al menos una pieza a generar");
        }

        ContenidoTemario contenido = contenidoTemarioRepository.findByTemarioId(temarioId)
                .orElseGet(() -> new ContenidoTemario(temario));

        ModeloIA modeloTier = request.modelo() != null ? request.modelo() : ModeloIA.FLASH;
        NivelAcademico nivelGeneracion = NivelAcademico.fromGradoAcademico(temario.getGradoAcademico());
        int numeroDiapositivas = resolverNumeroDiapositivas(modeloTier, request);
        int numeroParrafos = resolverNumeroParrafos(modeloTier, request);
        int numeroPreguntas = resolverNumeroPreguntas(modeloTier, request);
        int numeroModulos = resolverNumeroModulos(modeloTier, request);
        // Source text for estructura/teoría: prefers the already-generated outline
        // (estructura) so teoría develops exactly the topics/subtopics it defines, then
        // falls back to previously generated teoría (self-refinement on regeneration),
        // and finally to the raw syllabus description for a first-ever generation.
        String fuente = contenido.getEstructura() != null && !contenido.getEstructura().isBlank()
                ? contenido.getEstructura()
                : contenido.getTeoria() != null && !contenido.getTeoria().isBlank()
                        ? contenido.getTeoria()
                        : temario.getDescripcion();

        Set<PiezaMaterial> piezas = request.piezas();
        boolean generaTeoriaAhora = piezas.contains(PiezaMaterial.TEORIA);
        boolean requiereTeoriaExistente = piezas.contains(PiezaMaterial.EVALUACION)
                || piezas.contains(PiezaMaterial.DIAPOSITIVAS);
        String teoriaGuardada = contenido.getTeoria();
        if (requiereTeoriaExistente && !generaTeoriaAhora
                && (teoriaGuardada == null || teoriaGuardada.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Genera la teoría primero antes de generar evaluación o diapositivas");
        }

        // Evaluación/diapositivas are grounded in the theory text, never the raw syllabus
        // source: if teoría is part of this same request it must finish first so its
        // output can be reused; otherwise the already-persisted theory is reused as-is.
        CompletableFuture<String> teoriaFuture = generaTeoriaAhora
                ? aiContentGeneratorService.generarTeoria(
                        temario.getAsignatura().getNombre(),
                        temario.getTitulo(),
                        nivelGeneracion,
                        fuente,
                        modeloTier,
                        numeroParrafos)
                : CompletableFuture.completedFuture(teoriaGuardada);

        Map<PiezaMaterial, CompletableFuture<?>> futures = new EnumMap<>(PiezaMaterial.class);
        for (PiezaMaterial pieza : piezas) {
            futures.put(pieza, dispatch(pieza, temario, nivelGeneracion, fuente, teoriaFuture, modeloTier, numeroDiapositivas, numeroPreguntas, numeroModulos));
        }

        // Each failure is caught individually so one failed piece (e.g. an OpenAI
        // timeout) doesn't discard the pieces that generated successfully, and the
        // real error message reaches the API response instead of being logged only.
        Map<PiezaMaterial, String> fallos = new EnumMap<>(PiezaMaterial.class);
        Map<PiezaMaterial, CompletableFuture<Object>> resultados = new EnumMap<>(PiezaMaterial.class);
        futures.forEach((pieza, future) -> resultados.put(pieza, future.handle((valor, error) -> {
            if (error != null) {
                log.error("Pieza '{}' del temario {} falló", pieza.getValor(), temarioId, error);
                fallos.put(pieza, error.getMessage());
                return null;
            }
            return valor;
        })));

        return CompletableFuture.allOf(resultados.values().toArray(CompletableFuture[]::new))
                .thenApply(v -> {
                    boolean algunExito = false;
                    for (Map.Entry<PiezaMaterial, CompletableFuture<Object>> entry : resultados.entrySet()) {
                        Object resultado = entry.getValue().join();
                        if (resultado != null) {
                            aplicarResultado(contenido, entry.getKey(), resultado);
                            algunExito = true;
                        }
                    }

                    // Never fail the whole request over AI provider errors (rate limits,
                    // timeouts): report them per piece in piezasFallidas instead. modelo
                    // only advances when it actually produced something with the new tier.
                    if (algunExito) {
                        contenido.setModelo(modeloTier.getValor());
                        usuarioRepository.incrementAiGenerationCount(
                                temario.getUsuario().getId(),
                                resultados.values().stream().filter(future -> future.join() != null).count());
                    }
                    ContenidoTemario saved = contenidoTemarioRepository.save(contenido);
                    return mapToDTO(saved, fallos);
                });
    }

    private Temario findOwnedTemario(String temarioId, String userEmail) {
        Temario temario = temarioRepository.findById(temarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Temario no encontrado"));

        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado a este temario");
        }
        return temario;
    }

    /**
     * Resolves the slide count against the chosen tier: null falls back to the tier
     * default; an explicit value is validated against the tier's [min, max] range only
     * when slides are actually requested, rejecting out-of-range values with 400.
     */
    private int resolverNumeroDiapositivas(ModeloIA modeloTier, GenerarMaterialRequestDTO request) {
        Integer solicitado = request.numeroDiapositivas();
        if (solicitado == null) {
            return modeloTier.getDefaultDiapositivas();
        }
        if (request.piezas().contains(PiezaMaterial.DIAPOSITIVAS)
                && (solicitado < modeloTier.getMinDiapositivas() || solicitado > modeloTier.getMaxDiapositivas())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format(
                    "El número de diapositivas para el modelo %s debe estar entre %d y %d",
                    modeloTier.name(), modeloTier.getMinDiapositivas(), modeloTier.getMaxDiapositivas()));
        }
        return solicitado;
    }

    /**
     * Resolves the theory paragraph count against the chosen tier: null falls back to
     * the tier default; an explicit value is validated against the tier's [min, max]
     * range only when theory is actually requested, rejecting out-of-range values with 400.
     */
    private int resolverNumeroParrafos(ModeloIA modeloTier, GenerarMaterialRequestDTO request) {
        Integer solicitado = request.numeroParrafos();
        if (solicitado == null) {
            return modeloTier.getDefaultParrafosTeoria();
        }
        if (request.piezas().contains(PiezaMaterial.TEORIA)
                && (solicitado < modeloTier.getMinParrafosTeoria() || solicitado > modeloTier.getMaxParrafosTeoria())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format(
                    "El número de párrafos para el modelo %s debe estar entre %d y %d",
                    modeloTier.name(), modeloTier.getMinParrafosTeoria(), modeloTier.getMaxParrafosTeoria()));
        }
        return solicitado;
    }

    /**
     * Resolves the evaluation question count against the chosen tier: null falls back to
     * the tier default; an explicit value is validated against the tier's [min, max]
     * range only when an evaluation is actually requested, rejecting out-of-range values with 400.
     */
    private int resolverNumeroPreguntas(ModeloIA modeloTier, GenerarMaterialRequestDTO request) {
        Integer solicitado = request.numeroPreguntas();
        if (solicitado == null) {
            return modeloTier.getDefaultPreguntas();
        }
        if (request.piezas().contains(PiezaMaterial.EVALUACION)
                && (solicitado < modeloTier.getMinPreguntas() || solicitado > modeloTier.getMaxPreguntas())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format(
                    "El número de preguntas para el modelo %s debe estar entre %d y %d",
                    modeloTier.name(), modeloTier.getMinPreguntas(), modeloTier.getMaxPreguntas()));
        }
        return solicitado;
    }

    /**
     * Resolves the outline módulo (unidad) count against the chosen tier: null falls back
     * to the tier default; an explicit value is validated only when estructura is actually
     * requested. Unlike diapositivas/parrafos/preguntas, this is not a continuous [min, max]
     * range — each tier only allows its own two discrete options (compact/standard for
     * Tutor, detailed/exhaustive for Catedrático), rejecting anything else with 400.
     */
    private int resolverNumeroModulos(ModeloIA modeloTier, GenerarMaterialRequestDTO request) {
        Integer solicitado = request.numeroModulos();
        if (solicitado == null) {
            return modeloTier.getDefaultModulos();
        }
        if (request.piezas().contains(PiezaMaterial.ESTRUCTURA)
                && solicitado != modeloTier.getMinModulos() && solicitado != modeloTier.getMaxModulos()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format(
                    "El número de módulos para el modelo %s debe ser %d o %d",
                    modeloTier.name(), modeloTier.getMinModulos(), modeloTier.getMaxModulos()));
        }
        return solicitado;
    }

    /**
     * Dispatches a single piece. EVALUACION/DIAPOSITIVAS chain off {@code teoriaFuture}
     * so they always run after (and are grounded in) the theory text, whether it was
     * just generated in this same request or already persisted.
     */
    private CompletableFuture<?> dispatch(
            PiezaMaterial pieza, Temario temario, NivelAcademico nivel, String fuente, CompletableFuture<String> teoriaFuture,
            ModeloIA modeloTier, int numeroDiapositivas, int numeroPreguntas, int numeroModulos) {
        String asignatura = temario.getAsignatura().getNombre();
        String titulo = temario.getTitulo();
        String grado = temario.getGradoAcademico();
        return switch (pieza) {
            case ESTRUCTURA -> aiContentGeneratorService.generarEstructura(asignatura, titulo, nivel, fuente, modeloTier, numeroModulos);
            case TEORIA -> teoriaFuture;
            case EVALUACION -> teoriaFuture.thenCompose(teoriaTexto ->
                    aiContentGeneratorService.generarEvaluacion(asignatura, titulo, nivel, teoriaTexto, modeloTier, numeroPreguntas));
            case DIAPOSITIVAS -> teoriaFuture.thenCompose(teoriaTexto ->
                    aiContentGeneratorService.generarDiapositivas(asignatura, titulo, grado, teoriaTexto, modeloTier, numeroDiapositivas));
        };
    }

    @SuppressWarnings("unchecked")
    private void aplicarResultado(ContenidoTemario contenido, PiezaMaterial pieza, Object resultado) {
        switch (pieza) {
            case ESTRUCTURA -> contenido.setEstructura((String) resultado);
            case TEORIA -> contenido.setTeoria((String) resultado);
            case EVALUACION -> contenido.setEvaluacion((List<EvaluacionPreguntaDTO>) resultado);
            case DIAPOSITIVAS -> contenido.setDiapositivas((List<DiapositivaDTO>) resultado);
        }
    }

    private ContenidoTemarioResponseDTO mapToDTO(ContenidoTemario entity, Map<PiezaMaterial, String> fallos) {
        Map<String, String> piezasFallidas = new LinkedHashMap<>();
        fallos.forEach((pieza, mensaje) -> piezasFallidas.put(pieza.getValor(), mensaje));
        return new ContenidoTemarioResponseDTO(
                entity.getId(),
                entity.getTemario().getId(),
                entity.getEstructura(),
                entity.getTeoria(),
                entity.getEvaluacion(),
                entity.getDiapositivas(),
                entity.getModelo(),
                piezasFallidas
        );
    }
}
