package Katedra.Server.controller;

import Katedra.Server.dto.TemarioRequestDTO;
import Katedra.Server.dto.TemarioResponseDTO;
import Katedra.Server.dto.TemarioUploadResponseDTO;
import Katedra.Server.dto.TemarioUrlRequestDTO;
import Katedra.Server.model.ModeloGeneracion;
import Katedra.Server.service.TemarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller that exposes routes to manage syllabi (temarios)
 * and generate educational materials (theory, exercises, quizzes, slides).
 * All endpoints in this controller require active JWT Authentication.
 */
@RestController
@RequestMapping("/temarios")
public class TemarioController {

    private final TemarioService temarioService;
    private final Katedra.Server.service.ContenidoTemarioService contenidoTemarioService;
    private final Katedra.Server.service.HistorialEventoService historialEventoService;

    public TemarioController(
            TemarioService temarioService,
            Katedra.Server.service.ContenidoTemarioService contenidoTemarioService,
            Katedra.Server.service.HistorialEventoService historialEventoService) {
        this.temarioService = temarioService;
        this.contenidoTemarioService = contenidoTemarioService;
        this.historialEventoService = historialEventoService;
    }

    /**
     * Creates a new syllabus associated with the authenticated user.
     *
     * @param request the syllabus fields (title, description, degree, subject)
     * @param authentication the authenticated user token
     * @return the created syllabus details with HTTP 201 Created
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<TemarioResponseDTO>> createTemario(
            @Valid @RequestBody TemarioRequestDTO request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        TemarioResponseDTO created = temarioService.createTemario(userEmail, request);
        var generationRequest = new Katedra.Server.dto.GenerarMaterialRequestDTO(
                Set.of(Katedra.Server.model.PiezaMaterial.ESTRUCTURA),
                resolveModeloGeneracion(request.modeloGeneracion()), null, null, null, null,
                request.numeroModulos());
        return contenidoTemarioService.generarMaterial(created.id(), userEmail, generationRequest)
                .thenApply(content -> new ResponseEntity<>(created, HttpStatus.CREATED));
    }

    @PostMapping(value = "/cargar/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<TemarioUploadResponseDTO>> cargarTemarioArchivo(
            @RequestPart("file") MultipartFile file,
            @RequestParam String titulo,
            @RequestParam @NotBlank(message = "La asignatura es obligatoria") String asignaturaId,
            @RequestParam
            @NotBlank(message = "El grado académico es obligatorio")
            @Pattern(regexp = "^[^,;]+$", message = "Solo se permite un grado académico")
            String gradoAcademico,
            @RequestParam(defaultValue = "BASICO") ModeloGeneracion modeloGeneracion,
            @RequestParam(required = false) Integer numeroModulos,
            Authentication authentication) {
        String userEmail = authentication.getName();
        TemarioUploadResponseDTO created = temarioService.cargarTemarioArchivo(
                userEmail, file, titulo, asignaturaId, gradoAcademico);
        var generationRequest = new Katedra.Server.dto.GenerarMaterialRequestDTO(
                Set.of(Katedra.Server.model.PiezaMaterial.ESTRUCTURA),
                modeloGeneracion.toModeloIA(), null, null, null, null, numeroModulos);
        return contenidoTemarioService.generarMaterial(created.temario().id(), userEmail, generationRequest)
                .thenApply(content -> new ResponseEntity<>(created, HttpStatus.CREATED));
    }

    @PostMapping("/cargar/url")
    public CompletableFuture<ResponseEntity<TemarioUploadResponseDTO>> cargarTemarioUrl(
            @Valid @RequestBody TemarioUrlRequestDTO request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        TemarioUploadResponseDTO created = temarioService.cargarTemarioUrl(userEmail, request);
        var generationRequest = new Katedra.Server.dto.GenerarMaterialRequestDTO(
                Set.of(Katedra.Server.model.PiezaMaterial.ESTRUCTURA),
                resolveModeloGeneracion(request.modeloGeneracion()), null, null, null, null,
                request.numeroModulos());
        return contenidoTemarioService.generarMaterial(created.temario().id(), userEmail, generationRequest)
                .thenApply(content -> new ResponseEntity<>(created, HttpStatus.CREATED));
    }

    /**
     * Retrieves all syllabi belonging to the authenticated user.
     *
     * @param authentication the authenticated user token
     * @return a list of active syllabi for the user
     */
    @GetMapping
    public ResponseEntity<List<TemarioResponseDTO>> getMyTemarios(
            @RequestParam(required = false) String asignaturaId,
            Authentication authentication) {
        String userEmail = authentication.getName();
        List<TemarioResponseDTO> list = asignaturaId == null
                ? temarioService.getTemariosByUser(userEmail)
                : temarioService.getTemariosByAsignatura(userEmail, asignaturaId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/favoritos")
    public ResponseEntity<List<TemarioResponseDTO>> getFavoritos(Authentication authentication) {
        return ResponseEntity.ok(
                temarioService.getFavoritosByUser(authentication.getName()));
    }

    /**
     * Returns the authenticated user's total AI generation call count.
     *
     * @param authentication the authenticated user token
     * @return the user's generation statistics
     */
    @GetMapping("/estadisticas")
    public ResponseEntity<Katedra.Server.dto.TemarioStatsResponseDTO> getEstadisticas(Authentication authentication) {
        return ResponseEntity.ok(temarioService.getEstadisticas(authentication.getName()));
    }

    /**
     * Returns the authenticated user's read-only content history: temarios created, edited,
     * (un)favorited, had material generated, or deleted, newest first. Nothing here can be
     * removed by the caller — it is an audit trail, not a browsable/editable log.
     *
     * @param authentication the authenticated user token
     * @return the user's history events, newest first
     */
    @GetMapping("/historial")
    public ResponseEntity<List<Katedra.Server.dto.HistorialEventoResponseDTO>> getHistorial(Authentication authentication) {
        return ResponseEntity.ok(historialEventoService.listarHistorial(authentication.getName()));
    }

    /**
     * Retrieves the details of a specific syllabus by its ID.
     * The syllabus must belong to the authenticated user.
     *
     * @param id the unique UUID of the syllabus
     * @param authentication the authenticated user token
     * @return the syllabus details
     */
    @GetMapping("/{id}")
    public ResponseEntity<TemarioResponseDTO> getTemarioById(
            @PathVariable String id,
            Authentication authentication) {
        String userEmail = authentication.getName();
        TemarioResponseDTO temario = temarioService.getTemarioById(id, userEmail);
        return ResponseEntity.ok(temario);
    }

    /**
     * Updates the editable fields of a syllabus. The syllabus must belong to the
     * authenticated user.
     *
     * @param id the unique UUID of the syllabus
     * @param request the updated fields (title, description, degree, subject)
     * @param authentication the authenticated user token
     * @return the updated syllabus details
     */
    @PutMapping("/{id}")
    public ResponseEntity<TemarioResponseDTO> updateTemario(
            @PathVariable String id,
            @Valid @RequestBody TemarioRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(temarioService.updateTemario(id, authentication.getName(), request));
    }

    @PatchMapping("/{id}/favorito")
    public ResponseEntity<TemarioResponseDTO> updateFavorito(
            @PathVariable String id,
            @Valid @RequestBody Katedra.Server.dto.TemarioFavoritoRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(temarioService.updateFavorito(
                id, authentication.getName(), request.favorito()));
    }

    /**
     * Soft deletes a syllabus from the system by setting its deleted_at timestamp.
     *
     * @param id the unique UUID of the syllabus to delete
     * @param authentication the authenticated user token
     * @return HTTP 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemario(
            @PathVariable String id,
            Authentication authentication) {
        String userEmail = authentication.getName();
        temarioService.deleteTemario(id, userEmail);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves the generated materials (theory, exercises, quizzes, slides) of a syllabus.
     * Returns an error if materials have not been generated yet.
     *
     * @param id the unique UUID of the syllabus
     * @param authentication the authenticated user token
     * @return the generated materials DTO
     */
    @GetMapping("/{id}/contenido")
    public ResponseEntity<Katedra.Server.dto.ContenidoTemarioResponseDTO> getContenidoByTemarioId(
            @PathVariable String id,
            Authentication authentication) {
        String userEmail = authentication.getName();
        var contenido = contenidoTemarioService.getContenidoByTemarioId(id, userEmail);
        return ResponseEntity.ok(contenido);
    }

    @GetMapping("/{id}/fuente")
    public ResponseEntity<Katedra.Server.dto.ContenidoFuenteResponseDTO> getFuenteByTemarioId(
            @PathVariable String id,
            Authentication authentication) {
        return ResponseEntity.ok(
                contenidoTemarioService.getFuenteByTemarioId(id, authentication.getName()));
    }

    /**
     * Selectively generates educational material pieces for an existing syllabus.
     * Pieces that already exist are skipped unless explicitly listed for regeneration,
     * so OpenAI credits are never spent by accident.
     *
     * @param id the unique UUID of the syllabus
     * @param request pieces to generate, model tier, and pieces to force-regenerate
     * @param authentication the authenticated user token
     * @return the updated materials DTO, including any skipped pieces
     */
    @PostMapping("/{id}/generar-material")
    public CompletableFuture<ResponseEntity<Katedra.Server.dto.ContenidoTemarioResponseDTO>> generarMaterial(
            @PathVariable String id,
            @RequestBody Katedra.Server.dto.GenerarMaterialRequestDTO request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return contenidoTemarioService.generarMaterial(id, userEmail, request)
                .thenApply(ResponseEntity::ok);
    }

    private Katedra.Server.model.ModeloIA resolveModeloGeneracion(ModeloGeneracion modeloGeneracion) {
        return modeloGeneracion == null
                ? Katedra.Server.model.ModeloIA.FLASH
                : modeloGeneracion.toModeloIA();
    }
}
