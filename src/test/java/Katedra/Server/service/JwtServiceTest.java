package Katedra.Server.service;

import Katedra.Server.model.AuthProvider;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "dummysecretforlocaltestingonlypleasedonotuse1234";

    @Test
    void shouldIncludePersistedGoogleNameInToken() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 900_000L);

        Usuario usuario = new Usuario();
        usuario.setEmail("teacher@example.com");
        usuario.setNombre("María González");
        usuario.setAuthProvider(AuthProvider.GOOGLE);
        usuario.setProviderUserId("google-123");
        usuario.setRol(RolUsuario.ROLE_PROFESOR);

        String token = jwtService.generateToken(usuario);
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.get("nombre", String.class)).isEqualTo("María González");
    }
    @Test
    void shouldGenerateUniqueSessionIdsOnlyForStudentTokensAndKeepExpiration() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 900_000L);
        Usuario student = new Usuario("student@example.com", "hash", "Student", RolUsuario.ROLE_ALUMNO);

        Claims firstClaims = parse(jwtService.generateToken(student));
        Claims secondClaims = parse(jwtService.generateToken(student));

        assertThat(firstClaims.getId()).isNotBlank().isNotEqualTo(secondClaims.getId());
        assertThat(firstClaims.getExpiration().getTime() - firstClaims.getIssuedAt().getTime())
                .isBetween(899_000L, 900_001L);

        Usuario teacher = new Usuario("teacher@example.com", "hash", "Teacher", RolUsuario.ROLE_PROFESOR);
        assertThat(parse(jwtService.generateToken(teacher)).getId()).isNull();
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
