package Katedra.Server.controller;

import Katedra.Server.dto.AsignaturaRequestDTO;
import Katedra.Server.dto.AsignaturaResponseDTO;
import Katedra.Server.service.AsignaturaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AsignaturaController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class AsignaturaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AsignaturaService service;

    @MockitoBean
    private Katedra.Server.service.JwtService jwtService;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockitoBean
    private Katedra.Server.repository.UsuarioRepository usuarioRepository;

    private final org.springframework.security.authentication.UsernamePasswordAuthenticationToken principal =
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    "profesor@katedra.com", null);
    private final AsignaturaResponseDTO response =
            new AsignaturaResponseDTO("asignatura-1", "Programación", "Fundamentos");

    @Test
    void shouldCreateAsignatura() throws Exception {
        given(service.create(eq(principal.getName()), any(AsignaturaRequestDTO.class)))
                .willReturn(response);

        mockMvc.perform(post("/asignaturas")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Programación","descripcion":"Fundamentos"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/asignaturas/asignatura-1"))
                .andExpect(jsonPath("$.id").value("asignatura-1"));
    }

    @Test
    void shouldRejectInvalidAsignatura() throws Exception {
        mockMvc.perform(post("/asignaturas")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"  ","descripcion":"Fundamentos"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldListAsignaturas() throws Exception {
        given(service.findAll(principal.getName())).willReturn(List.of(response));

        mockMvc.perform(get("/asignaturas").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Programación"));
    }

    @Test
    void shouldGetAsignatura() throws Exception {
        given(service.findById("asignatura-1", principal.getName())).willReturn(response);

        mockMvc.perform(get("/asignaturas/asignatura-1").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Fundamentos"));
    }

    @Test
    void shouldDeleteAsignatura() throws Exception {
        mockMvc.perform(delete("/asignaturas/asignatura-1").principal(principal))
                .andExpect(status().isNoContent());

        verify(service).delete("asignatura-1", principal.getName());
    }
}
