---
name: security-hardening
description: OWASP security, input validation, and Spring Security hardening
---

# Security Hardening (Backend)

## Input Validation

Validate all input at the boundary (Controller layer). Never trust client-side validation alone. Use Jakarta Validation annotations on DTO records.

```java
public record AuthRegisterRequestDTO(
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    @Size(max = 255)
    String email,

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
    String password,

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    String nombre
) {}
```

Rules:
- Validate type, length, format, and range on every input via `@Valid` annotation in Controller endpoints.
- Avoid raw query parameters for complex filtering; use structured request bodies or validated query parameters.

## SQL Injection Prevention

Always use Spring Data JPA repository methods or parameterized queries with `@Query`. Never concatenate raw strings to construct SQL queries.

```java
// Spring Data JPA query methods are parameterized automatically
Optional<Usuario> findByEmail(String email);

// Parametric custom queries
@Query("SELECT u FROM Usuario u WHERE u.email = :email")
Optional<Usuario> buscarPorEmail(@Param("email") String email);
```

## JWT Best Practices

- Expose JWTs containing standard claims (subject, issued at, expiration, roles).
- Keep secret keys completely out of version control. Read them from environment variables (e.g. `${JWT_SECRET}`).
- Validate all incoming JWTs using a stateless filter (`JwtAuthenticationFilter`) before passing the request to downstream controllers.

## Secrets Management

- **Doppler CLI integration:** In development, run the application using `doppler run -- ./mvnw spring-boot:run`.
- **Properties file safety:** Never write database passwords, client secrets, or private tokens in `application.properties` directly. Use placeholders pointing to environment variables.

## Security Configuration (Spring Security)

Ensure that:
- CORS is configured to allow only authorized origins (e.g. the frontend client origin).
- CSRF is disabled only if the application is stateless (using JWT authentication).
- Endpoints are properly secured according to roles (e.g., `/usuarios/**` has role `ADMIN`, other endpoints require authentication).
