package Katedra.Server.controller;

import Katedra.Server.dto.AuthLoginRequestDTO;
import Katedra.Server.dto.AuthRegisterRequestDTO;
import Katedra.Server.dto.AuthResponseDTO;
import Katedra.Server.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.bind.annotation.CrossOrigin;

/**
 * REST Controller to handle user authentication endpoints.
 * Provides routes for registration and login using JWT tokens.
 */
@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user in the system.
     *
     * @param request the registration details containing email, password, and name
     * @return a response entity containing the signed JWT token and user profile details
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody AuthRegisterRequestDTO request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /**
     * Authenticates existing user credentials.
     *
     * @param request the login credentials containing email and password
     * @return a response entity containing the signed JWT token and user profile details
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthLoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Logs out the current user. JWT auth is stateless, so there is no server-side
     * session to invalidate; the client discards its token. This endpoint exists so
     * the frontend logout flow gets a successful acknowledgement (and gives us a
     * single place to hook token blacklisting later if needed).
     *
     * @return HTTP 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/google")
    public ResponseEntity<Void> googleLogin() {
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, "/api/v1/oauth2/authorization/google")
                .build();
    }

    @GetMapping("/microsoft")
    public ResponseEntity<Void> microsoftLogin() {
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, "/api/v1/oauth2/authorization/microsoft")
                .build();
    }
}
