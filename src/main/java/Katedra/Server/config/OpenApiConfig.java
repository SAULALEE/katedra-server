package Katedra.Server.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * Generates the OpenAPI spec and Swagger UI directly from the controllers (springdoc), rather
 * than hand-maintaining a spec file that can silently drift from the code — which is exactly
 * what happened to the previous hand-written openapi.yaml.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Katedra API",
                version = "1.0.0",
                description = "AI-driven academic content generator: syllabi, generation, exports, and billing."
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste the token returned by POST /auth/login or /auth/register."
)
public class OpenApiConfig {

    // Mirrors WebSecurityConfig's permitAll list exactly. Kept as a literal set here (rather
    // than shared code) because the two serve different purposes: WebSecurityConfig enforces
    // auth, this only decides whether Swagger UI shows a lock icon and sends a bearer token.
    // Springdoc paths are omitted — they document the API, they aren't part of it.
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/auth/register", "/auth/login", "/auth/logout", "/auth/google", "/auth/microsoft",
            "/webhooks/stripe", "/suscripciones/planes"
    );

    @Bean
    public OpenAPI katedraOpenApi() {
        return new OpenAPI().servers(List.of(
                new Server().url("http://localhost:8080/api/v1").description("Local"),
                new Server().url("https://katedra-server.onrender.com/api/v1").description("Production")
        ));
    }

    @Bean
    public GlobalOpenApiCustomizer bearerAuthRequirement() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                if (PUBLIC_PATHS.contains(path)) {
                    return;
                }
                for (Operation operation : item.readOperations()) {
                    operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                }
            });
        };
    }
}
