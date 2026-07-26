package Katedra.Server.config;

import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import Katedra.Server.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final int SC_PRECONDITION_REQUIRED = 428;

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            userEmail = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<Usuario> usuario = usuarioRepository.findByEmail(userEmail);

            // The token's signature already proves identity, so the password-gated
            // userDetailsService (which rejects social-login accounts with no local
            // password) has no business in this path: it would 500 every request
            // from a Google/Microsoft-authenticated user.
            if (usuario.isPresent() && jwtService.isTokenValid(jwt, userEmail)) {
                UserDetails userDetails = toUserDetails(usuario.get());
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                this.securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);

                if (!isPasswordChangeRequest(request) && usuario.get().isMustChangePassword()) {
                    response.setStatus(SC_PRECONDITION_REQUIRED);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"message\":\"Debes cambiar tu contraseña temporal antes de continuar.\"}");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private UserDetails toUserDetails(Usuario usuario) {
        return new User(
                usuario.getEmail(),
                usuario.getPassword() == null ? "" : usuario.getPassword(),
                List.of(new SimpleGrantedAuthority(usuario.getRol().name()))
        );
    }

    private boolean isPasswordChangeRequest(HttpServletRequest request) {
        return request.getRequestURI().endsWith("/usuarios/me/password");
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        return path.startsWith("/auth/")
                || request.getRequestURI().startsWith("/api/v1/auth/")
                // Stripe sends no Authorization header; the webhook signature is what
                // authenticates it. Without this the filter would also 428 the callback
                // for users flagged mustChangePassword.
                || path.startsWith("/webhooks/")
                || request.getRequestURI().startsWith("/api/v1/webhooks/")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/")
                || path.equals("/error")
                || request.getRequestURI().equals("/api/v1/error");
    }
}
