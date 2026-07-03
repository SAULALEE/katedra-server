package Katedra.Server.controller;

import Katedra.Server.dto.TemarioRequestDTO;
import Katedra.Server.dto.TemarioResponseDTO;
import Katedra.Server.service.ContenidoTemarioService;
import Katedra.Server.service.TemarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TemarioController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class TemarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TemarioService temarioService;

    @MockitoBean
    private ContenidoTemarioService contenidoTemarioService;

    @MockitoBean
    private Katedra.Server.service.JwtService jwtService;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private Principal mockPrincipal;
    private TemarioResponseDTO mockResponse;

    @BeforeEach
    void setUp() {
        mockPrincipal = new UsernamePasswordAuthenticationToken("profesor@katedra.com", null);
        mockResponse = new TemarioResponseDTO(
                "temario-uuid-123",
                "Curso de Java",
                "Aprende Java 21",
                "Universidad",
                "Programacion",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    void shouldCreateTemario() throws Exception {
        String jsonRequest = """
                {
                    "titulo": "Curso de Java",
                    "descripcion": "Aprende Java 21",
                    "gradoAcademico": "Universidad",
                    "asignatura": "Programacion"
                }
                """;
        given(temarioService.createTemario(eq("profesor@katedra.com"), any(TemarioRequestDTO.class)))
                .willReturn(mockResponse);

        mockMvc.perform(post("/temarios")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("temario-uuid-123"))
                .andExpect(jsonPath("$.titulo").value("Curso de Java"))
                .andExpect(jsonPath("$.descripcion").value("Aprende Java 21"));

        verify(temarioService).createTemario(eq("profesor@katedra.com"), any(TemarioRequestDTO.class));
    }

    @Test
    void shouldGetMyTemarios() throws Exception {
        given(temarioService.getTemariosByUser("profesor@katedra.com"))
                .willReturn(List.of(mockResponse));

        mockMvc.perform(get("/temarios")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("temario-uuid-123"))
                .andExpect(jsonPath("$[0].titulo").value("Curso de Java"));

        verify(temarioService).getTemariosByUser("profesor@katedra.com");
    }

    @Test
    void shouldGetTemarioById() throws Exception {
        given(temarioService.getTemarioById("temario-uuid-123", "profesor@katedra.com"))
                .willReturn(mockResponse);

        mockMvc.perform(get("/temarios/temario-uuid-123")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("temario-uuid-123"))
                .andExpect(jsonPath("$.titulo").value("Curso de Java"));

        verify(temarioService).getTemarioById("temario-uuid-123", "profesor@katedra.com");
    }

    @Test
    void shouldDeleteTemario() throws Exception {
        doNothing().when(temarioService).deleteTemario("temario-uuid-123", "profesor@katedra.com");

        mockMvc.perform(delete("/temarios/temario-uuid-123")
                        .principal(mockPrincipal))
                .andExpect(status().isNoContent());

        verify(temarioService).deleteTemario("temario-uuid-123", "profesor@katedra.com");
    }

    @Test
    void shouldGetContenidoByTemarioId() throws Exception {
        var contenidoResponse = new Katedra.Server.dto.ContenidoTemarioResponseDTO(
                "contenido-uuid-789", "temario-uuid-123", "## Teoría", "## Ejercicios", List.of(), List.of());
        given(contenidoTemarioService.getContenidoByTemarioId("temario-uuid-123", "profesor@katedra.com"))
                .willReturn(contenidoResponse);

        mockMvc.perform(get("/temarios/temario-uuid-123/contenido")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("contenido-uuid-789"))
                .andExpect(jsonPath("$.teoria").value("## Teoría"));

        verify(contenidoTemarioService).getContenidoByTemarioId("temario-uuid-123", "profesor@katedra.com");
    }

    @Test
    void shouldGenerarMaterialAsync() throws Exception {
        var contenidoResponse = new Katedra.Server.dto.ContenidoTemarioResponseDTO(
                "contenido-uuid-789", "temario-uuid-123", "## Teoría IA", "## Ejercicios IA", List.of(), List.of());
        given(contenidoTemarioService.generarMaterial("temario-uuid-123", "profesor@katedra.com"))
                .willReturn(CompletableFuture.completedFuture(contenidoResponse));

        var mvcResult = mockMvc.perform(post("/temarios/temario-uuid-123/generar-material")
                        .principal(mockPrincipal))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("contenido-uuid-789"))
                .andExpect(jsonPath("$.teoria").value("## Teoría IA"));

        verify(contenidoTemarioService).generarMaterial("temario-uuid-123", "profesor@katedra.com");
    }

    @Test
    void shouldGenerarMaterialDesdeCeroAsync() throws Exception {
        String jsonRequest = """
                {
                    "materia": "Programacion",
                    "tema": "Grafos",
                    "unidades": "Unidad 1: BFS"
                }
                """;
        var contenidoResponse = new Katedra.Server.dto.ContenidoTemarioResponseDTO(
                "contenido-nuevo-001", "temario-nuevo-001", "## Teoría IA", "## Ejercicios IA", List.of(), List.of());
        given(contenidoTemarioService.generarMaterialDesdeCero(
                any(Katedra.Server.dto.GenerarMaterialRequestDTO.class), eq("profesor@katedra.com")))
                .willReturn(CompletableFuture.completedFuture(contenidoResponse));

        var mvcResult = mockMvc.perform(post("/temarios/generar-material")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("contenido-nuevo-001"))
                .andExpect(jsonPath("$.temarioId").value("temario-nuevo-001"));

        verify(contenidoTemarioService).generarMaterialDesdeCero(
                any(Katedra.Server.dto.GenerarMaterialRequestDTO.class), eq("profesor@katedra.com"));
    }

    @Test
    void shouldReturn500WhenGeneracionFails() throws Exception {
        given(contenidoTemarioService.generarMaterial("temario-uuid-123", "profesor@katedra.com"))
                .willReturn(CompletableFuture.failedFuture(new RuntimeException("AI error")));

        var mvcResult = mockMvc.perform(post("/temarios/temario-uuid-123/generar-material")
                        .principal(mockPrincipal))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isInternalServerError());
    }
}
