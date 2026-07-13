package Katedra.Server.controller;

import Katedra.Server.dto.TemarioRequestDTO;
import Katedra.Server.dto.TemarioResponseDTO;
import Katedra.Server.dto.TemarioUploadResponseDTO;
import Katedra.Server.dto.TemarioDriveRequestDTO;
import Katedra.Server.dto.TemarioUrlRequestDTO;
import Katedra.Server.service.TemarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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

    public TemarioController(TemarioService temarioService, Katedra.Server.service.ContenidoTemarioService contenidoTemarioService) {
        this.temarioService = temarioService;
        this.contenidoTemarioService = contenidoTemarioService;
    }

    /**
     * Creates a new syllabus associated with the authenticated user.
     *
     * @param request the syllabus fields (title, description, degree, subject)
     * @param authentication the authenticated user token
     * @return the created syllabus details with HTTP 201 Created
     */
    @PostMapping
    public ResponseEntity<TemarioResponseDTO> createTemario(
            @Valid @RequestBody TemarioRequestDTO request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        TemarioResponseDTO created = temarioService.createTemario(userEmail, request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping(value = "/cargar/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TemarioUploadResponseDTO> cargarTemarioArchivo(
            @RequestPart("file") MultipartFile file,
            @RequestParam String titulo,
            @RequestParam String asignatura,
            @RequestParam String gradoAcademico,
            Authentication authentication) {
        String userEmail = authentication.getName();
        TemarioUploadResponseDTO created = temarioService.cargarTemarioArchivo(
                userEmail, file, titulo, asignatura, gradoAcademico);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping("/cargar/url")
    public ResponseEntity<TemarioUploadResponseDTO> cargarTemarioUrl(
            @RequestBody TemarioUrlRequestDTO request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        TemarioUploadResponseDTO created = temarioService.cargarTemarioUrl(userEmail, request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping("/cargar/drive")
    public ResponseEntity<TemarioUploadResponseDTO> cargarTemarioDrive(
            @RequestBody TemarioDriveRequestDTO request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        TemarioUploadResponseDTO created = temarioService.cargarTemarioDrive(userEmail, request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * Retrieves all syllabi belonging to the authenticated user.
     *
     * @param authentication the authenticated user token
     * @return a list of active syllabi for the user
     */
    @GetMapping
    public ResponseEntity<List<TemarioResponseDTO>> getMyTemarios(Authentication authentication) {
        String userEmail = authentication.getName();
        List<TemarioResponseDTO> list = temarioService.getTemariosByUser(userEmail);
        return ResponseEntity.ok(list);
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
}
