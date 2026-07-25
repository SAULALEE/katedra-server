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
}
