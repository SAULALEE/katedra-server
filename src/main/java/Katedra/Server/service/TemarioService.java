package Katedra.Server.service;

import Katedra.Server.dto.TemarioRequestDTO;
import Katedra.Server.dto.TemarioResponseDTO;
import Katedra.Server.dto.TemarioUploadResponseDTO;
import Katedra.Server.dto.TemarioUrlRequestDTO;
import Katedra.Server.dto.TemarioStatsResponseDTO;
import Katedra.Server.model.Asignatura;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.model.Temario;
import Katedra.Server.model.TipoEventoHistorial;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.AsignaturaRepository;
import Katedra.Server.repository.ContenidoTemarioRepository;
import Katedra.Server.repository.TemarioRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TemarioService {

    private final TemarioRepository temarioRepository;
    private final AsignaturaRepository asignaturaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContenidoTemarioRepository contenidoTemarioRepository;
    private final TemarioFileExtractionService temarioFileExtractionService;
    private final TemarioUrlExtractionService temarioUrlExtractionService;
    private final HistorialEventoService historialEventoService;

    public TemarioService(
            TemarioRepository temarioRepository,
            AsignaturaRepository asignaturaRepository,
            UsuarioRepository usuarioRepository,
            ContenidoTemarioRepository contenidoTemarioRepository,
            TemarioFileExtractionService temarioFileExtractionService,
            TemarioUrlExtractionService temarioUrlExtractionService,
            HistorialEventoService historialEventoService) {
        this.temarioRepository = temarioRepository;
        this.asignaturaRepository = asignaturaRepository;
        this.usuarioRepository = usuarioRepository;
        this.contenidoTemarioRepository = contenidoTemarioRepository;
        this.temarioFileExtractionService = temarioFileExtractionService;
        this.temarioUrlExtractionService = temarioUrlExtractionService;
        this.historialEventoService = historialEventoService;
    }

    @Transactional
    public TemarioResponseDTO createTemario(String userEmail, TemarioRequestDTO request) {
        validateSingleGradoAcademico(request.gradoAcademico());
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Temario temario = new Temario(
                usuario,
                request.titulo(),
                request.descripcion(),
                request.gradoAcademico(),
                findOwnedAsignatura(request.asignaturaId(), userEmail)
        );

        Temario saved = temarioRepository.save(temario);
        contenidoTemarioRepository.save(new ContenidoTemario(saved));
        historialEventoService.registrar(saved, TipoEventoHistorial.CREADO, null);
        return mapToDTO(saved);
    }

    @Transactional
    public TemarioResponseDTO updateTemario(String id, String userEmail, TemarioRequestDTO request) {
        validateSingleGradoAcademico(request.gradoAcademico());
        Temario temario = temarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Temario no encontrado"));
        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new RuntimeException("Acceso denegado a este temario");
        }

        temario.setTitulo(request.titulo());
        temario.setDescripcion(request.descripcion());
        temario.setGradoAcademico(request.gradoAcademico());
        temario.setAsignatura(findOwnedAsignatura(request.asignaturaId(), userEmail));
        temario.setUpdatedAt(java.time.LocalDateTime.now());
        Temario saved = temarioRepository.save(temario);
        historialEventoService.registrar(saved, TipoEventoHistorial.EDITADO, null);
        return mapToDTO(saved);
    }

    @Transactional
    public TemarioUploadResponseDTO cargarTemarioArchivo(
            String userEmail,
            MultipartFile file,
            String titulo,
            String asignaturaId,
            String gradoAcademico) {
        validateSingleGradoAcademico(gradoAcademico);
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Asignatura asignatura = findOwnedAsignatura(asignaturaId, userEmail);
        var extracted = temarioFileExtractionService.extract(file);
        String resolvedTitulo = resolveTitulo(titulo, extracted.filename());

        Temario temario = new Temario(
                usuario,
                resolvedTitulo,
                "Contenido cargado desde archivo: " + extracted.filename(),
                gradoAcademico,
                asignatura
        );
        Temario savedTemario = temarioRepository.save(temario);

        ContenidoTemario contenido = new ContenidoTemario(savedTemario);
        contenido.setTeoria(extracted.text());
        contenido.setContenidoFuente(extracted.text());
        ContenidoTemario savedContenido = contenidoTemarioRepository.save(contenido);
        historialEventoService.registrar(savedTemario, TipoEventoHistorial.CREADO, null);

        return new TemarioUploadResponseDTO(
                mapToDTO(savedTemario),
                savedContenido.getId(),
                extracted.filename(),
                extracted.contentType(),
                null,
                extracted.text().length()
        );
    }

    @Transactional
    public TemarioUploadResponseDTO cargarTemarioUrl(String userEmail, TemarioUrlRequestDTO request) {
        validateSingleGradoAcademico(request.gradoAcademico());
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Asignatura asignatura = findOwnedAsignatura(request.asignaturaId(), userEmail);
        var extracted = temarioUrlExtractionService.extract(request.url());
        String resolvedTitulo = resolveTitulo(request.titulo(), extracted.title());

        Temario temario = new Temario(
                usuario,
                resolvedTitulo,
                "Contenido cargado desde URL: " + extracted.url(),
                request.gradoAcademico(),
                asignatura
        );
        Temario savedTemario = temarioRepository.save(temario);

        ContenidoTemario contenido = new ContenidoTemario(savedTemario);
        contenido.setTeoria(extracted.text());
        contenido.setContenidoFuente(extracted.text());
        ContenidoTemario savedContenido = contenidoTemarioRepository.save(contenido);
        historialEventoService.registrar(savedTemario, TipoEventoHistorial.CREADO, null);

        return new TemarioUploadResponseDTO(
                mapToDTO(savedTemario),
                savedContenido.getId(),
                null,
                "text/html",
                extracted.url(),
                extracted.text().length()
        );
    }

    public List<TemarioResponseDTO> getTemariosByUser(String userEmail) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return temarioRepository.findByUsuarioIdOrderByCreatedAtAsc(usuario.getId())
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<TemarioResponseDTO> getTemariosByAsignatura(String userEmail, String asignaturaId) {
        if (asignaturaId == null || asignaturaId.isBlank()) {
            return java.util.Collections.emptyList();
        }
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        findOwnedAsignatura(asignaturaId, userEmail);
        return temarioRepository
                .findByUsuarioIdAndAsignaturaIdOrderByCreatedAtAsc(usuario.getId(), asignaturaId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<TemarioResponseDTO> getFavoritosByUser(String userEmail) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return temarioRepository.findByUsuarioIdAndFavoritoTrueOrderByCreatedAtAsc(usuario.getId())
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public TemarioResponseDTO getTemarioById(String id, String userEmail) {
        Temario temario = temarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Temario no encontrado"));
        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new RuntimeException("Acceso denegado a este temario");
        }
        return mapToDTO(temario);
    }

    @Transactional
    public TemarioResponseDTO updateFavorito(String id, String userEmail, boolean favorito) {
        Temario temario = temarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Temario no encontrado"));
        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Acceso denegado a este temario");
        }
        temario.setFavorito(favorito);
        temario.setUpdatedAt(java.time.LocalDateTime.now());
        Temario saved = temarioRepository.save(temario);
        historialEventoService.registrar(saved,
                favorito ? TipoEventoHistorial.FAVORITO_AGREGADO : TipoEventoHistorial.FAVORITO_QUITADO, null);
        return mapToDTO(saved);
    }

    @Transactional
    public void deleteTemario(String id, String userEmail) {
        Temario temario = temarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Temario no encontrado"));

        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new RuntimeException("Acceso denegado a este temario");
        }

        contenidoTemarioRepository.findByTemarioId(id).ifPresent(contenidoTemarioRepository::delete);
        temarioRepository.delete(temario);
        historialEventoService.registrar(temario, TipoEventoHistorial.ELIMINADO, null);
    }

    @Transactional(readOnly = true)
    public TemarioStatsResponseDTO getEstadisticas(String userEmail) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return new TemarioStatsResponseDTO(usuario.getAiGenerationCount());
    }

    private TemarioResponseDTO mapToDTO(Temario temario) {
        int progreso = 0;
        java.util.Optional<ContenidoTemario> ctOpt = contenidoTemarioRepository.findByTemarioId(temario.getId());
        if (ctOpt.isPresent()) {
            ContenidoTemario ct = ctOpt.get();
            if (ct.getEstructura() != null && !ct.getEstructura().isBlank() && !ct.getEstructura().equals("[]")) progreso += 25;
            if (ct.getTeoria() != null && !ct.getTeoria().isBlank() && !ct.getTeoria().equals("[]")) progreso += 25;
            if (ct.getEvaluacion() != null && !ct.getEvaluacion().isEmpty()) progreso += 25;
            if (ct.getDiapositivas() != null && !ct.getDiapositivas().isEmpty()) progreso += 25;
        }

        return new TemarioResponseDTO(
                temario.getId(),
                temario.getTitulo(),
                temario.getDescripcion(),
                temario.getGradoAcademico(),
                temario.getAsignatura().getId(),
                temario.getAsignatura().getNombre(),
                temario.isFavorito(),
                temario.getCreatedAt(),
                temario.getUpdatedAt(),
                progreso
        );
    }

    private String resolveTitulo(String titulo, String filename) {
        if (titulo != null && !titulo.isBlank()) {
            return titulo;
        }
        int extensionStart = filename.lastIndexOf('.');
        if (extensionStart > 0) {
            return filename.substring(0, extensionStart);
        }
        return filename;
    }

    private void validateSingleGradoAcademico(String gradoAcademico) {
        if (gradoAcademico == null || gradoAcademico.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El grado académico es obligatorio");
        }
        if (gradoAcademico.contains(",") || gradoAcademico.contains(";")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permite un grado académico");
        }
    }

    private Asignatura findOwnedAsignatura(String asignaturaId, String userEmail) {
        return asignaturaRepository.findByIdAndUsuarioEmail(asignaturaId, userEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Asignatura no encontrada"));
    }
}
