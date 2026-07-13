package Katedra.Server.controller;

import Katedra.Server.dto.TemarioRequestDTO;
import Katedra.Server.dto.TemarioResponseDTO;
import Katedra.Server.model.NivelAcademico;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
                NivelAcademico.UNIVERSITARIO,
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
                    "gradoAcademico": "universitario",
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
                "contenido-uuid-789", "temario-uuid-123", "## Teoría",
                List.of(), List.of(), "flash", Map.of());
        given(contenidoTemarioService.getContenidoByTemarioId("temario-uuid-123", "profesor@katedra.com"))
                .willReturn(contenidoResponse);

        mockMvc.perform(get("/temarios/temario-uuid-123/contenido")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("contenido-uuid-789"))
                .andExpect(jsonPath("$.teoria").value("## Teoría"))
                .andExpect(jsonPath("$.modelo").value("flash"));

        verify(contenidoTemarioService).getContenidoByTemarioId("temario-uuid-123", "profesor@katedra.com");
    }

    @Test
    void shouldGenerarMaterialSelectivoAsync() throws Exception {
        String jsonRequest = """
                {
                    "piezas": ["evaluacion"],
                    "modelo": "flash"
                }
                """;
        var contenidoResponse = new Katedra.Server.dto.ContenidoTemarioResponseDTO(
                "contenido-uuid-789", "temario-uuid-123", null,
                List.of(new Katedra.Server.dto.EvaluacionPreguntaDTO(
                        "¿Pregunta?", List.of("A", "B", "C", "D"), 0, "Explicación")),
                null, "flash", Map.of());
        given(contenidoTemarioService.generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                any(Katedra.Server.dto.GenerarMaterialRequestDTO.class)))
                .willReturn(CompletableFuture.completedFuture(contenidoResponse));

        var mvcResult = mockMvc.perform(post("/temarios/temario-uuid-123/generar-material")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("contenido-uuid-789"))
                .andExpect(jsonPath("$.evaluacion[0].pregunta").value("¿Pregunta?"))
                .andExpect(jsonPath("$.modelo").value("flash"));

        verify(contenidoTemarioService).generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                any(Katedra.Server.dto.GenerarMaterialRequestDTO.class));
    }

    @Test
    void shouldRejectInvalidModelo() throws Exception {
        String jsonRequest = """
                {
                    "piezas": ["teoria"],
                    "modelo": "gpt-99-turbo-hackeado"
                }
                """;

        mockMvc.perform(post("/temarios/temario-uuid-123/generar-material")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidPieza() throws Exception {
        String jsonRequest = """
                {
                    "piezas": ["memes"]
                }
                """;

        mockMvc.perform(post("/temarios/temario-uuid-123/generar-material")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenContenidoNotGenerated() throws Exception {
        given(contenidoTemarioService.getContenidoByTemarioId("temario-uuid-123", "profesor@katedra.com"))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Contenido no generado. Use POST /generar-material"));

        mockMvc.perform(get("/temarios/temario-uuid-123/contenido")
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn403WhenGenerarMaterialAccessDenied() throws Exception {
        String jsonRequest = """
                {
                    "piezas": ["teoria"]
                }
                """;
        given(contenidoTemarioService.generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                any(Katedra.Server.dto.GenerarMaterialRequestDTO.class)))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado a este temario"));

        mockMvc.perform(post("/temarios/temario-uuid-123/generar-material")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isForbidden());
    }
}
