package Katedra.Server.service;

import Katedra.Server.dto.ContenidoTemarioResponseDTO;
import Katedra.Server.dto.GenerarMaterialRequestDTO;
import Katedra.Server.dto.GenerarMaterialTemarioRequestDTO;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.model.Temario;
import Katedra.Server.repository.ContenidoTemarioRepository;
import Katedra.Server.repository.TemarioRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
public class ContenidoTemarioService {

    private static final Set<String> PIEZAS_PERMITIDAS = Set.of("teoria", "ejercicios", "evaluacion", "diapositivas");

    private final ContenidoTemarioRepository contenidoTemarioRepository;
    private final TemarioRepository temarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final AiContentGeneratorService aiContentGeneratorService;

    public ContenidoTemarioService(
            ContenidoTemarioRepository contenidoTemarioRepository,
            TemarioRepository temarioRepository,
            UsuarioRepository usuarioRepository,
            AiContentGeneratorService aiContentGeneratorService) {
        this.contenidoTemarioRepository = contenidoTemarioRepository;
        this.temarioRepository = temarioRepository;
        this.usuarioRepository = usuarioRepository;
        this.aiContentGeneratorService = aiContentGeneratorService;
    }

    @Transactional
    public ContenidoTemarioResponseDTO getContenidoByTemarioId(String temarioId, String userEmail) {
        var temario = temarioRepository.findById(temarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Temario no encontrado"));

        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado a este temario");
        }

        ContenidoTemario entity = contenidoTemarioRepository.findByTemarioId(temarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contenido no generado. Use POST /generar-material"));

        return mapToDTO(entity);
    }

    @Transactional
    public CompletableFuture<ContenidoTemarioResponseDTO> generarMaterial(String temarioId, String userEmail) {
        return generarMaterial(temarioId, userEmail, null);
    }

    @Transactional
    public CompletableFuture<ContenidoTemarioResponseDTO> generarMaterial(
            String temarioId,
            String userEmail,
            GenerarMaterialTemarioRequestDTO request) {
        var temario = temarioRepository.findById(temarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Temario no encontrado"));

        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado a este temario");
        }

        ModeloGeneracion modeloGeneracion = mapModelo(request == null ? null : request.modelo());
        List<String> piezasOmitidas = resolvePiezasOmitidas(request);

        return aiContentGeneratorService.generarContenido(
                        temario.getAsignatura(), temario.getTitulo(),
                        temario.getDescripcion(), temario.getGradoAcademico())
                .thenApply(aiContenido -> {
                    ContenidoTemario contenido = contenidoTemarioRepository
                            .findByTemarioId(temarioId).orElse(new ContenidoTemario(temario));
                    contenido.setTeoria(aiContenido.teoria());
                    contenido.setEjercicios(aiContenido.ejercicios());
                    contenido.setEvaluacion(aiContenido.evaluacion());
                    contenido.setDiapositivas(aiContenido.diapositivas());
                    return mapToDTO(contenidoTemarioRepository.save(contenido), piezasOmitidas);
                });
    }

    @Transactional
    public CompletableFuture<ContenidoTemarioResponseDTO> generarMaterialDesdeCero(
            GenerarMaterialRequestDTO request, String userEmail) {
        var usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        Temario temario = new Temario(usuario, request.tema(), request.unidades(), "Universitario", request.materia());
        var savedTemario = temarioRepository.save(temario);

        return aiContentGeneratorService.generarContenido(
                        request.materia(), request.tema(), request.unidades(), "Universitario")
                .thenApply(aiContenido -> {
                    ContenidoTemario contenido = new ContenidoTemario(savedTemario);
                    contenido.setTeoria(aiContenido.teoria());
                    contenido.setEjercicios(aiContenido.ejercicios());
                    contenido.setEvaluacion(aiContenido.evaluacion());
                    contenido.setDiapositivas(aiContenido.diapositivas());
                    return mapToDTO(contenidoTemarioRepository.save(contenido));
                });
    }

    private ContenidoTemarioResponseDTO mapToDTO(ContenidoTemario entity) {
        return mapToDTO(entity, List.of());
    }

    private ContenidoTemarioResponseDTO mapToDTO(ContenidoTemario entity, List<String> piezasOmitidas) {
        return new ContenidoTemarioResponseDTO(
                entity.getId(),
                entity.getTemario().getId(),
                entity.getTeoria(),
                entity.getEjercicios(),
                entity.getEvaluacion(),
                entity.getDiapositivas(),
                piezasOmitidas
        );
    }

    private ModeloGeneracion mapModelo(String modelo) {
        if ("gpt-4o".equals(modelo)) {
            return ModeloGeneracion.AVANZADO;
        }
        return ModeloGeneracion.BASICO;
    }

    private List<String> resolvePiezasOmitidas(GenerarMaterialTemarioRequestDTO request) {
        if (request == null) {
            return List.of();
        }
        List<String> piezasOmitidas = new ArrayList<>();
        addUnsupportedPiezas(request.piezas(), piezasOmitidas);
        addUnsupportedPiezas(request.regenerarPiezas(), piezasOmitidas);
        return piezasOmitidas;
    }

    private void addUnsupportedPiezas(List<String> piezas, List<String> piezasOmitidas) {
        if (piezas == null) {
            return;
        }
        for (String pieza : piezas) {
            if (pieza == null || !PIEZAS_PERMITIDAS.contains(pieza)) {
                piezasOmitidas.add(pieza);
            }
        }
    }

    private enum ModeloGeneracion {
        BASICO,
        AVANZADO
    }
}
