package Katedra.Server.config;

import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

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
}
