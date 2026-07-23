package Katedra.Server.service;

import Katedra.Server.dto.TemarioRequestDTO;
import Katedra.Server.dto.TemarioResponseDTO;
import Katedra.Server.dto.TemarioUploadResponseDTO;
import Katedra.Server.dto.TemarioDriveRequestDTO;
import Katedra.Server.dto.TemarioUrlRequestDTO;
import Katedra.Server.dto.TemarioStatsResponseDTO;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.model.Temario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.ContenidoTemarioRepository;
import Katedra.Server.repository.TemarioRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TemarioService {

    private final TemarioRepository temarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContenidoTemarioRepository contenidoTemarioRepository;
    private final TemarioFileExtractionService temarioFileExtractionService;
    private final TemarioUrlExtractionService temarioUrlExtractionService;
    private final TemarioGoogleDriveDownloadService temarioGoogleDriveDownloadService;

    public TemarioService(
            TemarioRepository temarioRepository,
            UsuarioRepository usuarioRepository,
            ContenidoTemarioRepository contenidoTemarioRepository,
            TemarioFileExtractionService temarioFileExtractionService,
            TemarioUrlExtractionService temarioUrlExtractionService,
            TemarioGoogleDriveDownloadService temarioGoogleDriveDownloadService) {
        this.temarioRepository = temarioRepository;
        this.usuarioRepository = usuarioRepository;
        this.contenidoTemarioRepository = contenidoTemarioRepository;
        this.temarioFileExtractionService = temarioFileExtractionService;
        this.temarioUrlExtractionService = temarioUrlExtractionService;
        this.temarioGoogleDriveDownloadService = temarioGoogleDriveDownloadService;
    }

    @Transactional
    public TemarioResponseDTO createTemario(String userEmail, TemarioRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Temario temario = new Temario(
                usuario,
                request.titulo(),
                request.descripcion(),
                request.gradoAcademico(),
                request.asignatura()
        );

        Temario saved = temarioRepository.save(temario);
        contenidoTemarioRepository.save(new ContenidoTemario(saved));
        return mapToDTO(saved);
    }

    @Transactional
    public TemarioResponseDTO updateTemario(String id, String userEmail, TemarioRequestDTO request) {
        Temario temario = temarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Temario no encontrado"));
        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new RuntimeException("Acceso denegado a este temario");
        }

        temario.setTitulo(request.titulo());
        temario.setDescripcion(request.descripcion());
        temario.setGradoAcademico(request.gradoAcademico());
        temario.setAsignatura(request.asignatura());
        temario.setUpdatedAt(java.time.LocalDateTime.now());
        return mapToDTO(temarioRepository.save(temario));
    }

    @Transactional
    public TemarioUploadResponseDTO cargarTemarioArchivo(
            String userEmail,
            MultipartFile file,
            String titulo,
            String asignatura,
            String gradoAcademico) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
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
        ContenidoTemario savedContenido = contenidoTemarioRepository.save(contenido);

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
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        var extracted = temarioUrlExtractionService.extract(request.url());
        String resolvedTitulo = resolveTitulo(request.titulo(), extracted.title());

        Temario temario = new Temario(
                usuario,
                resolvedTitulo,
                "Contenido cargado desde URL: " + extracted.url(),
                request.gradoAcademico(),
                request.asignatura()
        );
        Temario savedTemario = temarioRepository.save(temario);

        ContenidoTemario contenido = new ContenidoTemario(savedTemario);
        contenido.setTeoria(extracted.text());
        ContenidoTemario savedContenido = contenidoTemarioRepository.save(contenido);

        return new TemarioUploadResponseDTO(
                mapToDTO(savedTemario),
                savedContenido.getId(),
                null,
                "text/html",
                extracted.url(),
                extracted.text().length()
        );
    }

    @Transactional
    public TemarioUploadResponseDTO cargarTemarioDrive(String userEmail, TemarioDriveRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        var downloaded = temarioGoogleDriveDownloadService.download(request.url());
        var extracted = temarioFileExtractionService.extract(downloaded.file());
        String resolvedTitulo = resolveTitulo(request.titulo(), extracted.filename());

        Temario temario = new Temario(
                usuario,
                resolvedTitulo,
                "Contenido cargado desde Google Drive: " + downloaded.fileId(),
                request.gradoAcademico(),
                request.asignatura()
        );
        Temario savedTemario = temarioRepository.save(temario);

        ContenidoTemario contenido = new ContenidoTemario(savedTemario);
        contenido.setTeoria(extracted.text());
        ContenidoTemario savedContenido = contenidoTemarioRepository.save(contenido);

        return new TemarioUploadResponseDTO(
                mapToDTO(savedTemario),
                savedContenido.getId(),
                extracted.filename(),
                extracted.contentType(),
                request.url(),
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

    public List<String> getAsignaturasByUser(String userEmail) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return temarioRepository.findDistinctAsignaturasByUsuarioId(usuario.getId());
    }

    public List<TemarioResponseDTO> getTemariosByAsignatura(String userEmail, String asignatura) {
        if (asignatura == null || asignatura.isBlank()) {
            return java.util.Collections.emptyList();
        }
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return temarioRepository
                .findByUsuarioIdAndAsignaturaOrderByCreatedAtAsc(usuario.getId(), asignatura)
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
    public void deleteTemario(String id, String userEmail) {
        Temario temario = temarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Temario no encontrado"));

        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new RuntimeException("Acceso denegado a este temario");
        }

        contenidoTemarioRepository.findByTemarioId(id).ifPresent(contenidoTemarioRepository::delete);
        temarioRepository.delete(temario);
    }

    @Transactional(readOnly = true)
    public TemarioStatsResponseDTO getEstadisticas(String userEmail) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return new TemarioStatsResponseDTO(usuario.getAiGenerationCount());
    }

    private TemarioResponseDTO mapToDTO(Temario temario) {
        return new TemarioResponseDTO(
                temario.getId(),
                temario.getTitulo(),
                temario.getDescripcion(),
                temario.getGradoAcademico(),
                temario.getAsignatura(),
                temario.getCreatedAt(),
                temario.getUpdatedAt()
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
}
