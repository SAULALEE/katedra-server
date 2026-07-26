package Katedra.Server.config;

import Katedra.Server.dto.AuthResponseDTO;
import Katedra.Server.dto.UsuarioDTO;
import Katedra.Server.exception.SocialAccountConflictException;
import Katedra.Server.model.AuthProvider;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class OAuth2AuthenticationSuccessHandlerTest {

    private static final String SUCCESS_URL = "http://localhost:5173/auth/callback";
    private static final String ERROR_URL = "http://localhost:5173/auth/error";

    @Test
    void shouldRedirectKatedraJwtToAuthCallback() throws Exception {
        AuthService authService = mock(AuthService.class);
        OAuth2AuthenticationSuccessHandler handler = handler(authService);
        OAuth2User principal = googlePrincipal();
        OAuth2AuthenticationToken authentication =
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        given(authService.loginOrRegisterSocial(
                AuthProvider.GOOGLE, "google-123", "teacher@example.com", "Teacher"))
                .willReturn(new AuthResponseDTO(
                        "header.payload.signature",
                        new UsuarioDTO("user-1", "teacher@example.com", "Teacher", RolUsuario.ROLE_PROFESOR),
                        false));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo(SUCCESS_URL + "?token=header.payload.signature");
    }

    @Test
    void shouldRedirectLocalAccountConflictWithControlledError() throws Exception {
        AuthService authService = mock(AuthService.class);
        OAuth2AuthenticationSuccessHandler handler = handler(authService);
        OAuth2User principal = googlePrincipal();
        OAuth2AuthenticationToken authentication =
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        given(authService.loginOrRegisterSocial(
                AuthProvider.GOOGLE, "google-123", "teacher@example.com", "Teacher"))
                .willThrow(new SocialAccountConflictException(
                        "local_account_exists",
                        "Este correo debe iniciar sesión mediante contraseña."));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl())
                .contains(ERROR_URL)
                .contains("error=local_account_exists")
                .contains("message=Este%20correo%20debe%20iniciar%20sesi%C3%B3n%20mediante%20contrase%C3%B1a.");
    }

    private OAuth2AuthenticationSuccessHandler handler(AuthService authService) {
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(authService);
        ReflectionTestUtils.setField(handler, "frontendSuccessUrl", SUCCESS_URL);
        ReflectionTestUtils.setField(handler, "frontendErrorUrl", ERROR_URL);
        return handler;
    }

    private OAuth2User googlePrincipal() {
        return new DefaultOAuth2User(
                List.of(() -> "ROLE_USER"),
                Map.of(
                        "sub", "google-123",
                        "email", "teacher@example.com",
                        "name", "Teacher"),
                "sub");
    }
}
