package Katedra.Server.controller;

import Katedra.Server.dto.ExportacionArchivoDTO;
import Katedra.Server.service.ExportacionMaterialService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Downloads a piece of generated material as a file.
 *
 * <p>{@code pieza} and {@code formato} select a representation of the same resource, so a single
 * endpoint covers every combination and adding a format needs no controller change.
 *
 * <p>The content type is set on the response, never through {@code produces}: the global exception
 * handler always answers JSON, and a media-type restriction would turn a 403/404 into a 406.
 */
@RestController
@RequestMapping("/temarios/{temarioId}/exportaciones")
public class ExportacionController {

    private final ExportacionMaterialService exportacionMaterialService;

    public ExportacionController(ExportacionMaterialService exportacionMaterialService) {
        this.exportacionMaterialService = exportacionMaterialService;
    }

    @GetMapping
    public ResponseEntity<byte[]> exportar(
            @PathVariable String temarioId,
            @RequestParam String pieza,
            @RequestParam String formato,
            @RequestParam(required = false) String theme,
            Authentication authentication) {

        ExportacionArchivoDTO archivo = exportacionMaterialService.exportar(
                temarioId, authentication.getName(), pieza, formato, theme);

        ContentDisposition disposicion = ContentDisposition.attachment()
                .filename(archivo.nombreArchivo(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.mediaType()))
                .contentLength(archivo.contenido().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(archivo.contenido());
    }
}
