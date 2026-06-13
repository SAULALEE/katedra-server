package Katedra.Server.controller;

import Katedra.Server.dto.TemarioRequestDTO;
import Katedra.Server.dto.TemarioResponseDTO;
import Katedra.Server.service.TemarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/temarios")
public class TemarioController {

    private final TemarioService temarioService;

    public TemarioController(TemarioService temarioService) {
        this.temarioService = temarioService;
    }

    @PostMapping
    public ResponseEntity<TemarioResponseDTO> createTemario(
            @RequestBody TemarioRequestDTO request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        TemarioResponseDTO created = temarioService.createTemario(userEmail, request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TemarioResponseDTO>> getMyTemarios(Authentication authentication) {
        String userEmail = authentication.getName();
        List<TemarioResponseDTO> list = temarioService.getTemariosByUser(userEmail);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemarioResponseDTO> getTemarioById(
            @PathVariable String id,
            Authentication authentication) {
        String userEmail = authentication.getName();
        TemarioResponseDTO temario = temarioService.getTemarioById(id, userEmail);
        return ResponseEntity.ok(temario);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemario(
            @PathVariable String id,
            Authentication authentication) {
        String userEmail = authentication.getName();
        temarioService.deleteTemario(id, userEmail);
        return ResponseEntity.noContent().build();
    }
}
