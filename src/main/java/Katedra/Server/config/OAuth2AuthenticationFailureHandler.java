package Katedra.Server.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);

    @Value("${app.frontend.oauth.error-url}")
    private String frontendErrorUrl;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        logger.error("""
                ================ GOOGLE OAUTH ERROR ================
                Clase: {}
                Mensaje: {}
                Causa: {}
                ====================================================
                """,
                exception.getClass().getName(),
                exception.getMessage(),
                exception.getCause() != null ? exception.getCause().toString() : "null");
        logger.error("Google OAuth stack trace", exception);

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendErrorUrl)
                .queryParam("error", "social_auth_failed")
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
