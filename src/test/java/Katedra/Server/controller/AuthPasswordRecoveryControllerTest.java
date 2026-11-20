package Katedra.Server.controller;

import Katedra.Server.model.RolUsuario;
import Katedra.Server.service.AuthService;
import Katedra.Server.service.JwtService;
import Katedra.Server.repository.UsuarioRepository;
import Katedra.Server.delivery.PasswordRecoveryLinkDelivery;
import Katedra.Server.dto.GeneratedPasswordRecoveryTokenDTO;
import Katedra.Server.service.PasswordRecoveryService;
import Katedra.Server.student.service.StudentSessionRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class AuthPasswordRecoveryControllerTest {

    private static final String GENERIC_MESSAGE =
            "Si existe una cuenta asociada a este correo, recibirás instrucciones para recuperar tu contraseña.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordRecoveryService passwordRecoveryService;

    @MockitoBean
    private PasswordRecoveryLinkDelivery linkDelivery;

    @MockitoBean
    private StudentSessionRevocationService studentSessionRevocationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @Test
    void shouldAcceptStudentRecoveryRequestWithoutReturningTheToken() throws Exception {
        String email = "student@example.com";
        String token = "secret-recovery-token";
        LocalDateTime expiresAt = LocalDateTime.of(2026, 11, 18, 22, 0);
        given(passwordRecoveryService.generateToken(email))
                .willReturn(Optional.of(new GeneratedPasswordRecoveryTokenDTO(token, expiresAt, RolUsuario.ROLE_ALUMNO)));

        String response = mockMvc.perform(post("/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(token).doesNotContain(email);
        ArgumentCaptor<URI> linkCaptor = ArgumentCaptor.forClass(URI.class);
        verify(linkDelivery).deliver(eq(email), linkCaptor.capture(), eq(expiresAt));
        assertThat(linkCaptor.getValue())
                .hasToString("http://localhost:5173/alumnos/restablecer-contrasena?token=" + token);
    }

    @Test
    void shouldLinkTeachersToTheTeacherResetPage() throws Exception {
        String email = "teacher@example.com";
        LocalDateTime expiresAt = LocalDateTime.of(2026, 11, 18, 22, 0);
        given(passwordRecoveryService.generateToken(email))
                .willReturn(Optional.of(new GeneratedPasswordRecoveryTokenDTO("teacher-token", expiresAt, RolUsuario.ROLE_PROFESOR)));

        mockMvc.perform(post("/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"teacher@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));

        ArgumentCaptor<URI> linkCaptor = ArgumentCaptor.forClass(URI.class);
        verify(linkDelivery).deliver(eq(email), linkCaptor.capture(), eq(expiresAt));
        assertThat(linkCaptor.getValue()).hasToString("http://localhost:5173/restablecer-contrasena?token=teacher-token");
    }

    @Test
    void shouldReturnSameAcceptedResponseWhenNoTokenIsIssued() throws Exception {
        given(passwordRecoveryService.generateToken("missing@example.com")).willReturn(Optional.empty());

        mockMvc.perform(post("/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));

        verify(linkDelivery, never()).deliver(eq("missing@example.com"),
                org.mockito.ArgumentMatchers.any(URI.class), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectMalformedEmailBeforeCallingRecoveryService() throws Exception {
        mockMvc.perform(post("/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        verify(passwordRecoveryService, never()).generateToken("not-an-email");
    }

    @Test
    void shouldResetPasswordAndReturnNoContent() throws Exception {
        String token = "valid-recovery-token";
        String newPassword = "newPassword123";

        mockMvc.perform(post("/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));

        verify(passwordRecoveryService).resetPassword(token, newPassword);
    }

    @Test
    void shouldReturnGenericErrorForInvalidRecoveryToken() throws Exception {
        String token = "invalid-recovery-token";
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token de recuperación inválido o expirado"))
                .when(passwordRecoveryService).resetPassword(token, "newPassword123");

        mockMvc.perform(post("/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"newPassword123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token de recuperación inválido o expirado"));
    }

    @Test
    void shouldRejectInvalidNewPasswordBeforeCallingRecoveryService() throws Exception {
        mockMvc.perform(post("/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"valid-recovery-token\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));

        verify(passwordRecoveryService, never()).resetPassword("valid-recovery-token", "short");
    }

    @Test
    void shouldKeepLogoutSuccessfulWithoutChangingThePublicLogoutContract() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/logout"))
                .andExpect(status().isNoContent());

        verify(studentSessionRevocationService, never()).revoke(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldPassOnlyTheBearerValueToSessionRevocation() throws Exception {
        String token = "student-session-token";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        verify(studentSessionRevocationService).revoke(token);
    }
}
