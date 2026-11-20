package Katedra.Server.config;

import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import Katedra.Server.service.JwtService;
import Katedra.Server.student.repository.StudentSessionRevocationRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private StudentSessionRevocationRepository studentSessionRevocationRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldRequireAuthenticationForStudentProfile() throws Exception {
        mockMvc.perform(get("/students/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "student@example.com", roles = "ALUMNO")
    void shouldReturnOnlyBasicProfileForAuthenticatedStudent() throws Exception {
        Usuario student = new Usuario(
                "student@example.com", "sensitive-password-hash", "Student", RolUsuario.ROLE_ALUMNO);
        ReflectionTestUtils.setField(student, "id", "student-id");
        given(usuarioRepository.findByEmail("student@example.com")).willReturn(Optional.of(student));

        mockMvc.perform(get("/students/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("student-id"))
                .andExpect(jsonPath("$.email").value("student@example.com"))
                .andExpect(jsonPath("$.nombre").value("Student"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/asignaturas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "teacher@example.com", roles = "PROFESOR")
    void shouldForbidTeacherFromStudentProfileAndKeepTeacherRoutesProtected() throws Exception {
        mockMvc.perform(get("/students/me"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void shouldForbidAdminFromStudentProfile() throws Exception {
        mockMvc.perform(get("/students/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectRevokedStudentJwtWithUnauthorized() throws Exception {
        Usuario student = new Usuario(
                "student@example.com", "hash", "Student", RolUsuario.ROLE_ALUMNO);
        ReflectionTestUtils.setField(student, "id", "student-id");
        given(usuarioRepository.findByEmail(student.getEmail())).willReturn(Optional.of(student));
        String token = jwtService.generateToken(student);
        given(studentSessionRevocationRepository.existsBySessionKeyHashAndExpiresAtAfter(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .willReturn(true);

        mockMvc.perform(get("/students/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectExpiredStudentJwtWithUnauthorized() throws Exception {
        String expiredToken = Jwts.builder()
                .subject("student@example.com")
                .issuedAt(new Date(System.currentTimeMillis() - 2_000L))
                .expiration(new Date(System.currentTimeMillis() - 1_000L))
                .signWith(Keys.hmacShaKeyFor("dummysecretforlocaltestingonlypleasedonotuse1234"
                        .getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/students/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }
}
