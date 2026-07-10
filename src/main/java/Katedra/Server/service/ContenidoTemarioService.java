package Katedra.Server.service;

import Katedra.Server.dto.ContenidoTemarioResponseDTO;
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
import java.util.concurrent.CompletableFuture;

@Service
public class ContenidoTemarioService {

    private static final Logger log = LoggerFactory.getLogger(ContenidoTemarioService.class);

    private final ContenidoTemarioRepository contenidoTemarioRepository;
    private final TemarioRepository temarioRepository;
    private final AiContentGeneratorService aiContentGeneratorService;

    public ContenidoTemarioService(
            ContenidoTemarioRepository contenidoTemarioRepository,
            TemarioRepository temarioRepository,
            AiContentGeneratorService aiContentGeneratorService) {
        this.contenidoTemarioRepository = contenidoTemarioRepository;
        this.temarioRepository = temarioRepository;
        this.aiContentGeneratorService = aiContentGeneratorService;
    }

    @Transactional
    public ContenidoTemarioResponseDTO getContenidoByTemarioId(String temarioId, String userEmail) {
        findOwnedTemario(temarioId, userEmail);

        ContenidoTemario entity = contenidoTemarioRepository.findByTemarioId(temarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contenido no generado. Use POST /generar-material"));

        return mapToDTO(entity, Map.of());
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
        int numeroDiapositivas = resolverNumeroDiapositivas(modeloTier, request);
        // Source text for the prompts; once PDF/web ingestion lands this prefers temario source content.
        String fuente = temario.getDescripcion();

        Map<PiezaMaterial, CompletableFuture<?>> futures = new EnumMap<>(PiezaMaterial.class);
        for (PiezaMaterial pieza : request.piezas()) {
            futures.put(pieza, dispatch(pieza, temario, fuente, modeloTier, numeroDiapositivas));
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

    private CompletableFuture<?> dispatch(
            PiezaMaterial pieza, Temario temario, String fuente, ModeloIA modeloTier, int numeroDiapositivas) {
        String asignatura = temario.getAsignatura();
        String titulo = temario.getTitulo();
        NivelAcademico nivel = temario.getGradoAcademico();
        String grado = nivel.getEtiqueta();
        return switch (pieza) {
            case TEORIA -> aiContentGeneratorService.generarTeoria(asignatura, titulo, nivel, fuente, modeloTier);
            case EJERCICIOS -> aiContentGeneratorService.generarEjercicios(asignatura, titulo, grado, fuente, modeloTier);
            case EVALUACION -> aiContentGeneratorService.generarEvaluacion(asignatura, titulo, grado, fuente, modeloTier);
            case DIAPOSITIVAS -> aiContentGeneratorService.generarDiapositivas(asignatura, titulo, grado, fuente, modeloTier, numeroDiapositivas);
        };
    }

    @SuppressWarnings("unchecked")
    private void aplicarResultado(ContenidoTemario contenido, PiezaMaterial pieza, Object resultado) {
        switch (pieza) {
            case TEORIA -> contenido.setTeoria((String) resultado);
            case EJERCICIOS -> contenido.setEjercicios((String) resultado);
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
                entity.getTeoria(),
                entity.getEjercicios(),
                entity.getEvaluacion(),
                entity.getDiapositivas(),
                entity.getModelo(),
                piezasFallidas
        );
    }
}
