package Katedra.Server.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;
    private final ObjectProvider<OAuth2AuthenticationSuccessHandler> oAuth2AuthenticationSuccessHandlerProvider;
    private final ObjectProvider<OAuth2AuthenticationFailureHandler> oAuth2AuthenticationFailureHandlerProvider;
    @Value("${app.frontend.oauth.success-url:}")
    private String frontendSuccessUrl;
    @Value("${app.frontend.oauth.error-url:}")
    private String frontendErrorUrl;

    public WebSecurityConfig(
            AuthenticationProvider authenticationProvider,
            JwtAuthenticationFilter jwtAuthFilter,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            ObjectProvider<OAuth2AuthenticationSuccessHandler> oAuth2AuthenticationSuccessHandlerProvider,
            ObjectProvider<OAuth2AuthenticationFailureHandler> oAuth2AuthenticationFailureHandlerProvider
    ) {
        this.authenticationProvider = authenticationProvider;
        this.jwtAuthFilter = jwtAuthFilter;
        this.clientRegistrationRepositoryProvider = clientRegistrationRepositoryProvider;
        this.oAuth2AuthenticationSuccessHandlerProvider = oAuth2AuthenticationSuccessHandlerProvider;
        this.oAuth2AuthenticationFailureHandlerProvider = oAuth2AuthenticationFailureHandlerProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Public endpoints
                .requestMatchers("/auth/**", "/api/v1/auth/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/error").permitAll()
                // ROLE_ADMIN: user management only
                .requestMatchers("/usuarios/**").hasRole("ADMIN")
                // ROLE_PROFESOR: academic features only
                .requestMatchers("/asignaturas/**").hasRole("PROFESOR")
                .requestMatchers("/temarios/**").hasRole("PROFESOR")
                .requestMatchers("/assistant/**").hasRole("PROFESOR")
                // Deny everything else
                .anyRequest().denyAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN))
                .authenticationEntryPoint((request, response, authException) ->
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        ClientRegistrationRepository clientRegistrationRepository = clientRegistrationRepositoryProvider.getIfAvailable();
        OAuth2AuthenticationSuccessHandler successHandler = oAuth2AuthenticationSuccessHandlerProvider.getIfAvailable();
        OAuth2AuthenticationFailureHandler failureHandler = oAuth2AuthenticationFailureHandlerProvider.getIfAvailable();

        if (clientRegistrationRepository != null
                && successHandler != null
                && failureHandler != null
                && hasText(frontendSuccessUrl)
                && hasText(frontendErrorUrl)) {
            http.oauth2Login(oauth -> oauth
                    .successHandler(successHandler)
                    .failureHandler(failureHandler)
            );
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
