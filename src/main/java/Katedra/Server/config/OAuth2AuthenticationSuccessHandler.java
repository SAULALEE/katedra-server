package Katedra.Server.config;

import Katedra.Server.dto.AuthResponseDTO;
import Katedra.Server.exception.SocialAccountConflictException;
import Katedra.Server.model.AuthProvider;
import Katedra.Server.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final AuthService authService;

    @Value("${app.frontend.oauth.success-url}")
    private String frontendSuccessUrl;
    @Value("${app.frontend.oauth.error-url}")
    private String frontendErrorUrl;

    public OAuth2AuthenticationSuccessHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        try {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            OAuth2User oauth2User = oauthToken.getPrincipal();
            AuthProvider authProvider = resolveProvider(oauthToken.getAuthorizedClientRegistrationId());

            String providerUserId = resolveProviderUserId(authProvider, oauth2User);
            String email = resolveEmail(authProvider, oauth2User);
            String nombre = resolveName(oauth2User, email);

            AuthResponseDTO authResponse = authService.loginOrRegisterSocial(authProvider, providerUserId, email, nombre);
            String redirectUrl = UriComponentsBuilder.fromUriString(frontendSuccessUrl)
                    .queryParam("token", authResponse.token())
                    .build()
                    .toUriString();

            response.sendRedirect(redirectUrl);
        } catch (SocialAccountConflictException exception) {
            redirectToError(response, exception.getErrorCode(), exception.getMessage());
        } catch (Exception exception) {
            logger.error("OAuth completion failed: {}", exception.getClass().getSimpleName());
            redirectToError(response, "social_auth_failed", "No se pudo completar el inicio de sesión social.");
        }
    }

    private void redirectToError(HttpServletResponse response, String errorCode, String message) throws IOException {
        String redirectUrl = UriComponentsBuilder.fromUriString(frontendErrorUrl)
                .queryParam("error", errorCode)
                .queryParam("message", message)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        response.sendRedirect(redirectUrl);
    }

    private AuthProvider resolveProvider(String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> AuthProvider.GOOGLE;
            case "microsoft" -> AuthProvider.MICROSOFT;
            default -> throw new RuntimeException("Proveedor OAuth no soportado");
        };
    }

    private String resolveProviderUserId(AuthProvider provider, OAuth2User oauth2User) {
        String providerUserId = oauth2User.getAttribute("sub");
        if (provider == AuthProvider.MICROSOFT && providerUserId == null) {
            providerUserId = oauth2User.getAttribute("oid");
        }
        return providerUserId;
    }

    private String resolveEmail(AuthProvider provider, OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        if (provider == AuthProvider.MICROSOFT && email == null) {
            email = oauth2User.getAttribute("preferred_username");
        }
        return email;
    }

    private String resolveName(OAuth2User oauth2User, String email) {
        String name = oauth2User.getAttribute("name");
        return (name == null || name.isBlank()) ? email : name;
    }
}
