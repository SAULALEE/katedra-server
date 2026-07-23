package Katedra.Server.controller;

import Katedra.Server.dto.AsignaturaRequestDTO;
import Katedra.Server.dto.AsignaturaResponseDTO;
import Katedra.Server.service.AsignaturaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/asignaturas")
public class AsignaturaController {

    private final AsignaturaService asignaturaService;

    public AsignaturaController(AsignaturaService asignaturaService) {
        this.asignaturaService = asignaturaService;
    }

    @PostMapping
    public ResponseEntity<AsignaturaResponseDTO> create(
            @Valid @RequestBody AsignaturaRequestDTO request,
            Authentication authentication) {
        AsignaturaResponseDTO created = asignaturaService.create(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/asignaturas/" + created.id())).body(created);
    }

    @GetMapping
    public ResponseEntity<List<AsignaturaResponseDTO>> findAll(Authentication authentication) {
        return ResponseEntity.ok(asignaturaService.findAll(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AsignaturaResponseDTO> findById(
            @PathVariable String id,
            Authentication authentication) {
        return ResponseEntity.ok(asignaturaService.findById(id, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            Authentication authentication) {
        asignaturaService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
