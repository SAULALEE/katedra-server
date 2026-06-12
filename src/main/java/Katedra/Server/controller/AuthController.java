package Katedra.Server.controller;

import Katedra.Server.dto.AuthLoginRequestDTO;
import Katedra.Server.dto.AuthRegisterRequestDTO;
import Katedra.Server.dto.AuthResponseDTO;
import Katedra.Server.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody AuthRegisterRequestDTO request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthLoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
