package Katedra.Server.service;

import Katedra.Server.dto.ContenidoTemarioResponseDTO;
import Katedra.Server.dto.GenerarMaterialRequestDTO;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.model.Temario;
import Katedra.Server.repository.ContenidoTemarioRepository;
import Katedra.Server.repository.TemarioRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
public class ContenidoTemarioService {

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
                .orElseThrow(() -> new RuntimeException("Temario no encontrado"));

        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new RuntimeException("Acceso denegado a este temario");
        }

        ContenidoTemario entity = contenidoTemarioRepository.findByTemarioId(temarioId)
                .orElseThrow(() -> new RuntimeException("Contenido no generado. Use POST /generar-material"));

        return mapToDTO(entity);
    }

    @Transactional
    public CompletableFuture<ContenidoTemarioResponseDTO> generarMaterial(String temarioId, String userEmail) {
        var temario = temarioRepository.findById(temarioId)
                .orElseThrow(() -> new RuntimeException("Temario no encontrado"));

        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new RuntimeException("Acceso denegado a este temario");
        }

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
                    return mapToDTO(contenidoTemarioRepository.save(contenido));
                });
    }

    @Transactional
    public CompletableFuture<ContenidoTemarioResponseDTO> generarMaterialDesdeCero(
            GenerarMaterialRequestDTO request, String userEmail) {
        var usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

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
        return new ContenidoTemarioResponseDTO(
                entity.getId(),
                entity.getTemario().getId(),
                entity.getTeoria(),
                entity.getEjercicios(),
                entity.getEvaluacion(),
                entity.getDiapositivas()
        );
    }
}
