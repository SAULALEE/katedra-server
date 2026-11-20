package Katedra.Server.controller;

import Katedra.Server.dto.AuthLoginRequestDTO;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.dto.AuthRegisterRequestDTO;
import Katedra.Server.dto.AuthResponseDTO;
import Katedra.Server.dto.PasswordRecoveryAcceptedResponseDTO;
import Katedra.Server.dto.PasswordRecoveryRequestDTO;
import Katedra.Server.dto.PasswordResetRequestDTO;
import Katedra.Server.service.AuthService;
import Katedra.Server.delivery.PasswordRecoveryLinkDelivery;
import Katedra.Server.student.service.StudentSessionRevocationService;
import Katedra.Server.service.PasswordRecoveryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Set;


/**
 * REST Controller to handle user authentication endpoints.
 * Provides routes for registration and login using JWT tokens.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final PasswordRecoveryLinkDelivery passwordRecoveryLinkDelivery;
    private final StudentSessionRevocationService studentSessionRevocationService;
    private static final String TEACHER_RESET_PATH = "/restablecer-contrasena";
    private static final String STUDENT_RESET_PATH = "/alumnos/restablecer-contrasena";

    private final String frontendBaseUrl;
    private final Validator validator;

    public AuthController(
            AuthService authService,
            PasswordRecoveryService passwordRecoveryService,
            PasswordRecoveryLinkDelivery passwordRecoveryLinkDelivery,
            StudentSessionRevocationService studentSessionRevocationService,
            @Value("${app.frontend.base-url:http://localhost:5173}")
            String frontendBaseUrl,
            Validator validator
    ) {
        this.authService = authService;
        this.passwordRecoveryService = passwordRecoveryService;
        this.passwordRecoveryLinkDelivery = passwordRecoveryLinkDelivery;
        this.studentSessionRevocationService = studentSessionRevocationService;
        this.frontendBaseUrl = frontendBaseUrl;
        this.validator = validator;
    }

    /**
     * Registers a new user in the system.
     *
     * @param request the registration details containing email, password, and name
     * @return a response entity containing the signed JWT token and user profile details
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody AuthRegisterRequestDTO request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/students/register")
    public ResponseEntity<AuthResponseDTO> registerStudent(@Valid @RequestBody AuthRegisterRequestDTO request) {
        return ResponseEntity.ok(authService.registerStudent(request));
    }

    /**
     * Authenticates existing user credentials.
     *
     * @param request the login credentials containing email and password
     * @return a response entity containing the signed JWT token and user profile details
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody AuthLoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<PasswordRecoveryAcceptedResponseDTO> forgotPassword(
            @Valid @RequestBody PasswordRecoveryRequestDTO request
    ) {
        passwordRecoveryService.generateToken(request.email()).ifPresent(generated -> {
            String resetPath = generated.rol() == RolUsuario.ROLE_ALUMNO ? STUDENT_RESET_PATH : TEACHER_RESET_PATH;
            URI recoveryLink = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                    .path(resetPath)
                    .queryParam("token", generated.token())
                    .build()
                    .encode()
                    .toUri();
            try {
                passwordRecoveryLinkDelivery.deliver(request.email(), recoveryLink, generated.expiresAt());
            } catch (RuntimeException ignored) {
                // Do not log delivery exceptions: they may contain the recovery link.
            }
        });

        return ResponseEntity.accepted().body(new PasswordRecoveryAcceptedResponseDTO(
                "Si existe una cuenta asociada a este correo, recibirás instrucciones para recuperar tu contraseña."
        ));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@RequestBody PasswordResetRequestDTO request) {
        Set<ConstraintViolation<PasswordResetRequestDTO>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        passwordRecoveryService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
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
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            studentSessionRevocationService.revoke(authorizationHeader.substring(7));
        }
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
