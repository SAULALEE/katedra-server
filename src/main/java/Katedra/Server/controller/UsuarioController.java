package Katedra.Server.controller;

import Katedra.Server.dto.AuthResponseDTO;
import Katedra.Server.dto.PasswordChangeRequestDTO;
import Katedra.Server.dto.UsuarioCreateRequestDTO;
import Katedra.Server.dto.UsuarioCreateResponseDTO;
import Katedra.Server.dto.UsuarioDTO;
import Katedra.Server.dto.UsuarioUpdateRequestDTO;
import Katedra.Server.service.AuthService;
import Katedra.Server.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final AuthService authService;

    public UsuarioController(UsuarioService usuarioService, AuthService authService) {
        this.usuarioService = usuarioService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<List<UsuarioDTO>> getUsuarios() {
        return ResponseEntity.ok(usuarioService.getAllUsuarios());
    }

    @PostMapping
    public ResponseEntity<UsuarioCreateResponseDTO> createUsuario(@RequestBody UsuarioCreateRequestDTO request) {
        UsuarioCreateResponseDTO created = usuarioService.createAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/me/password")
    public ResponseEntity<AuthResponseDTO> changeMyPassword(
            Authentication authentication,
            @Valid @RequestBody PasswordChangeRequestDTO request
    ) {
        return ResponseEntity.ok(authService.changePassword(authentication.getName(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioDTO> getUsuarioById(@PathVariable String id) {
        return ResponseEntity.ok(usuarioService.getUsuarioById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioDTO> updateUsuario(
            @PathVariable String id,
            @RequestBody UsuarioUpdateRequestDTO request
    ) {
        return ResponseEntity.ok(usuarioService.updateUsuario(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUsuario(
            @PathVariable String id,
            Authentication authentication
    ) {
        usuarioService.deleteUsuario(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
