package Katedra.Server.controller;

import Katedra.Server.dto.TemarioRequestDTO;
import Katedra.Server.dto.TemarioResponseDTO;
import Katedra.Server.dto.TemarioUploadResponseDTO;
import Katedra.Server.dto.TemarioUrlRequestDTO;
import Katedra.Server.model.NivelAcademico;
import Katedra.Server.model.PiezaMaterial;
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
import java.util.Set;
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
    private Katedra.Server.service.HistorialEventoService historialEventoService;

    @MockitoBean
    private Katedra.Server.service.JwtService jwtService;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockitoBean
    private Katedra.Server.repository.UsuarioRepository usuarioRepository;

    private Principal mockPrincipal;
    private TemarioResponseDTO mockResponse;

    @BeforeEach
    void setUp() {
        mockPrincipal = new UsernamePasswordAuthenticationToken("profesor@katedra.com", null);
        mockResponse = new TemarioResponseDTO(
                "temario-uuid-123",
                "Curso de Java",
                "Aprende Java 21",
                "universitario",
                "asignatura-1",
                "Programacion",
                false,
                LocalDateTime.now(),
                LocalDateTime.now(),
                0
        );
    }

    @Test
    void shouldCreateTemario() throws Exception {
        String jsonRequest = """
                {
                    "titulo": "Curso de Java",
                    "descripcion": "Aprende Java 21",
                    "gradoAcademico": "universitario",
                    "asignaturaId": "asignatura-1"
                }
                """;
        given(temarioService.createTemario(eq("profesor@katedra.com"), any(TemarioRequestDTO.class)))
                .willReturn(mockResponse);
        var generatedContent = new Katedra.Server.dto.ContenidoTemarioResponseDTO(
                "contenido-uuid-789", "temario-uuid-123", "## Estructura generada", null,
                null, null, "basico", Map.of());
        given(contenidoTemarioService.generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                any(Katedra.Server.dto.GenerarMaterialRequestDTO.class)))
                .willReturn(CompletableFuture.completedFuture(generatedContent));

        var mvcResult = mockMvc.perform(post("/temarios")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("temario-uuid-123"))
                .andExpect(jsonPath("$.titulo").value("Curso de Java"))
                .andExpect(jsonPath("$.descripcion").value("Aprende Java 21"));

        verify(temarioService).createTemario(eq("profesor@katedra.com"), any(TemarioRequestDTO.class));
        verify(contenidoTemarioService).generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                org.mockito.ArgumentMatchers.argThat(request ->
                        request.piezas().equals(Set.of(PiezaMaterial.ESTRUCTURA))
                                && request.modelo() == Katedra.Server.model.ModeloIA.FLASH));
    }

    @Test
    void shouldPassNumeroModulosFromRequestToGenerationRequest() throws Exception {
        String jsonRequest = """
                {
                    "titulo": "Curso de Java",
                    "descripcion": "Aprende Java 21",
                    "gradoAcademico": "universitario",
                    "asignaturaId": "asignatura-1",
                    "numeroModulos": 6
                }
                """;
        given(temarioService.createTemario(eq("profesor@katedra.com"), any(TemarioRequestDTO.class)))
                .willReturn(mockResponse);
        var generatedContent = new Katedra.Server.dto.ContenidoTemarioResponseDTO(
                "contenido-uuid-789", "temario-uuid-123", "## Estructura generada", null,
                null, null, "basico", Map.of());
        given(contenidoTemarioService.generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                any(Katedra.Server.dto.GenerarMaterialRequestDTO.class)))
                .willReturn(CompletableFuture.completedFuture(generatedContent));

        var mvcResult = mockMvc.perform(post("/temarios")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isCreated());

        verify(contenidoTemarioService).generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                org.mockito.ArgumentMatchers.argThat(request -> Integer.valueOf(6).equals(request.numeroModulos())));
    }

    @Test
    void shouldRejectMultipleAcademicGradesForManualTemario() throws Exception {
        String jsonRequest = """
                {
                    "titulo": "Curso multidisciplinario",
                    "descripcion": "Contenido manual",
                    "gradoAcademico": "Primaria, Universidad, Diplomado de programacion",
                    "asignaturaId": "asignatura-1"
                }
                """;
        mockMvc.perform(post("/temarios")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(temarioService);
    }

    @Test
    void shouldRejectMultipleAcademicGradesForFileUpload() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "temario.md", "text/markdown", "# Temario".getBytes());

        mockMvc.perform(multipart("/temarios/cargar/archivo")
                        .file(file)
                        .param("titulo", "Curso")
                        .param("asignaturaId", "asignatura-1")
                        .param("gradoAcademico", "Primaria, Universidad")
                        .principal(mockPrincipal))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(temarioService);
    }

    @Test
    void shouldUploadTemarioFile() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "temario.md", "text/markdown", "# Temario".getBytes());
        var uploadResponse = new TemarioUploadResponseDTO(
                mockResponse, "contenido-uuid-789", "temario.md", "text/markdown", null, 9);
        given(temarioService.cargarTemarioArchivo(
                eq("profesor@katedra.com"),
                any(org.springframework.web.multipart.MultipartFile.class),
                eq("Curso de Java"),
                eq("asignatura-1"),
                eq("Universidad")))
                .willReturn(uploadResponse);
        given(contenidoTemarioService.generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                any(Katedra.Server.dto.GenerarMaterialRequestDTO.class)))
                .willReturn(CompletableFuture.completedFuture(
                        new Katedra.Server.dto.ContenidoTemarioResponseDTO(
                                "contenido-uuid-789", "temario-uuid-123", "## Estructura generada", null,
                                null, null, "basico", Map.of())));

        var mvcResult = mockMvc.perform(multipart("/temarios/cargar/archivo")
                        .file(file)
                        .param("titulo", "Curso de Java")
                        .param("asignaturaId", "asignatura-1")
                        .param("gradoAcademico", "Universidad")
                        .param("modeloGeneracion", "CATEDRATICO")
                        .principal(mockPrincipal))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temario.id").value("temario-uuid-123"))
                .andExpect(jsonPath("$.contenidoId").value("contenido-uuid-789"))
                .andExpect(jsonPath("$.archivoNombre").value("temario.md"));

        verify(temarioService).cargarTemarioArchivo(
                eq("profesor@katedra.com"),
                any(org.springframework.web.multipart.MultipartFile.class),
                eq("Curso de Java"),
                eq("asignatura-1"),
                eq("Universidad"));
        verify(contenidoTemarioService).generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                org.mockito.ArgumentMatchers.argThat(request ->
                        request.piezas().equals(Set.of(PiezaMaterial.ESTRUCTURA))
                                && request.modelo() == Katedra.Server.model.ModeloIA.PRO));
    }

    @Test
    void shouldPassNumeroModulosFromArchivoRequestToGenerationRequest() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "temario.md", "text/markdown", "# Temario".getBytes());
        var uploadResponse = new TemarioUploadResponseDTO(
                mockResponse, "contenido-uuid-789", "temario.md", "text/markdown", null, 9);
        given(temarioService.cargarTemarioArchivo(
                eq("profesor@katedra.com"),
                any(org.springframework.web.multipart.MultipartFile.class),
                eq("Curso de Java"),
                eq("asignatura-1"),
                eq("Universidad")))
                .willReturn(uploadResponse);
        given(contenidoTemarioService.generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                any(Katedra.Server.dto.GenerarMaterialRequestDTO.class)))
                .willReturn(CompletableFuture.completedFuture(
                        new Katedra.Server.dto.ContenidoTemarioResponseDTO(
                                "contenido-uuid-789", "temario-uuid-123", "## Estructura generada", null,
                                null, null, "avanzado", Map.of())));

        var mvcResult = mockMvc.perform(multipart("/temarios/cargar/archivo")
                        .file(file)
                        .param("titulo", "Curso de Java")
                        .param("asignaturaId", "asignatura-1")
                        .param("gradoAcademico", "Universidad")
                        .param("modeloGeneracion", "CATEDRATICO")
                        .param("numeroModulos", "8")
                        .principal(mockPrincipal))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isCreated());

        verify(contenidoTemarioService).generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                org.mockito.ArgumentMatchers.argThat(request -> Integer.valueOf(8).equals(request.numeroModulos())));
    }

    @Test
    void shouldUploadTemarioUrl() throws Exception {
        String jsonRequest = """
                {
                    "url": "https://example.com/temario",
                    "titulo": "Curso Web",
                    "asignaturaId": "asignatura-1",
                    "gradoAcademico": "Universidad",
                    "modeloGeneracion": "CATEDRATICO"
                }
                """;
        var uploadResponse = new TemarioUploadResponseDTO(
                mockResponse, "contenido-uuid-790", null, "text/html", "https://example.com/temario", 120);
        given(temarioService.cargarTemarioUrl(
                eq("profesor@katedra.com"),
                any(TemarioUrlRequestDTO.class)))
                .willReturn(uploadResponse);
        given(contenidoTemarioService.generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                any(Katedra.Server.dto.GenerarMaterialRequestDTO.class)))
                .willReturn(CompletableFuture.completedFuture(
                        new Katedra.Server.dto.ContenidoTemarioResponseDTO(
                                "contenido-uuid-790", "temario-uuid-123", "## Estructura generada", null,
                                null, null, "basico", Map.of())));

        var mvcResult = mockMvc.perform(post("/temarios/cargar/url")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temario.id").value("temario-uuid-123"))
                .andExpect(jsonPath("$.contenidoId").value("contenido-uuid-790"))
                .andExpect(jsonPath("$.fuenteUrl").value("https://example.com/temario"));

        verify(temarioService).cargarTemarioUrl(
                eq("profesor@katedra.com"),
                any(TemarioUrlRequestDTO.class));
        verify(contenidoTemarioService).generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                org.mockito.ArgumentMatchers.argThat(request ->
                        request.piezas().equals(Set.of(PiezaMaterial.ESTRUCTURA))
                                && request.modelo() == Katedra.Server.model.ModeloIA.PRO));
    }

    @Test
    void shouldPassNumeroModulosFromUrlRequestToGenerationRequest() throws Exception {
        String jsonRequest = """
                {
                    "url": "https://example.com/temario",
                    "titulo": "Curso Web",
                    "asignaturaId": "asignatura-1",
                    "gradoAcademico": "Universidad",
                    "modeloGeneracion": "CATEDRATICO",
                    "numeroModulos": 10
                }
                """;
        var uploadResponse = new TemarioUploadResponseDTO(
                mockResponse, "contenido-uuid-790", null, "text/html", "https://example.com/temario", 120);
        given(temarioService.cargarTemarioUrl(
                eq("profesor@katedra.com"),
                any(TemarioUrlRequestDTO.class)))
                .willReturn(uploadResponse);
        given(contenidoTemarioService.generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                any(Katedra.Server.dto.GenerarMaterialRequestDTO.class)))
                .willReturn(CompletableFuture.completedFuture(
                        new Katedra.Server.dto.ContenidoTemarioResponseDTO(
                                "contenido-uuid-790", "temario-uuid-123", "## Estructura generada", null,
                                null, null, "avanzado", Map.of())));

        var mvcResult = mockMvc.perform(post("/temarios/cargar/url")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isCreated());

        verify(contenidoTemarioService).generarMaterial(
                eq("temario-uuid-123"), eq("profesor@katedra.com"),
                org.mockito.ArgumentMatchers.argThat(request -> Integer.valueOf(10).equals(request.numeroModulos())));
    }

    @Test
    void shouldRejectUnsupportedGenerationModel() throws Exception {
        String jsonRequest = """
                {
                    "titulo": "Curso",
                    "descripcion": "Contenido",
                    "gradoAcademico": "Universidad",
                    "asignaturaId": "asignatura-1",
                    "modeloGeneracion": "FLASH"
                }
                """;

        mockMvc.perform(post("/temarios")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(temarioService);
    }

    @Test
    void shouldNotExposeGoogleDriveUploadEndpoint() throws Exception {
        mockMvc.perform(post("/temarios/cargar/drive")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectMultipleAcademicGradesForUrlUpload() throws Exception {
        String jsonRequest = """
                {
                    "url": "https://example.com/temario",
                    "titulo": "Curso Web",
                    "asignaturaId": "asignatura-1",
                    "gradoAcademico": "Primaria, Universidad"
                }
                """;

        mockMvc.perform(post("/temarios/cargar/url")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(temarioService);
    }

    @Test
    void shouldGetMyTemarios() throws Exception {
        given(temarioService.getTemariosByUser("profesor@katedra.com"))
                .willReturn(List.of(mockResponse));

        mockMvc.perform(get("/temarios")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("temario-uuid-123"))
                .andExpect(jsonPath("$[0].titulo").value("Curso de Java"))
                .andExpect(jsonPath("$[0].favorito").value(false));

        verify(temarioService).getTemariosByUser("profesor@katedra.com");
    }

    @Test
    void shouldListAuthenticatedUserFavorites() throws Exception {
        given(temarioService.getFavoritosByUser("profesor@katedra.com"))
                .willReturn(List.of(mockResponse));

        mockMvc.perform(get("/temarios/favoritos").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("temario-uuid-123"))
                .andExpect(jsonPath("$[0].favorito").value(false));

        verify(temarioService).getFavoritosByUser("profesor@katedra.com");
    }

    @Test
    void shouldMarkTemarioAsFavorite() throws Exception {
        var favoriteResponse = new TemarioResponseDTO(
                mockResponse.id(), mockResponse.titulo(), mockResponse.descripcion(),
                mockResponse.gradoAcademico(), mockResponse.asignaturaId(),
                mockResponse.asignatura(), true, mockResponse.createdAt(), mockResponse.updatedAt(), 0);
        given(temarioService.updateFavorito(
                "temario-uuid-123", "profesor@katedra.com", true))
                .willReturn(favoriteResponse);

        mockMvc.perform(patch("/temarios/temario-uuid-123/favorito")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorito\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorito").value(true));

        verify(temarioService).updateFavorito(
                "temario-uuid-123", "profesor@katedra.com", true);
    }

    @Test
    void shouldUnmarkTemarioAsFavorite() throws Exception {
        given(temarioService.updateFavorito(
                "temario-uuid-123", "profesor@katedra.com", false))
                .willReturn(mockResponse);

        mockMvc.perform(patch("/temarios/temario-uuid-123/favorito")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorito\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorito").value(false));
    }

    @Test
    void shouldGetAuthenticatedProfessorTemariosFilteredByAsignatura() throws Exception {
        given(temarioService.getTemariosByAsignatura("profesor@katedra.com", "asignatura-1"))
                .willReturn(List.of(mockResponse));

        mockMvc.perform(get("/temarios")
                        .param("asignaturaId", "asignatura-1")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("temario-uuid-123"))
                .andExpect(jsonPath("$[0].asignatura").value("Programacion"));

        verify(temarioService).getTemariosByAsignatura("profesor@katedra.com", "asignatura-1");
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
    void shouldUpdateTemario() throws Exception {
        given(temarioService.updateTemario(eq("temario-uuid-123"), eq("profesor@katedra.com"), any(TemarioRequestDTO.class)))
                .willReturn(mockResponse);

        mockMvc.perform(put("/temarios/temario-uuid-123")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Curso de Java","descripcion":"Actualizado","gradoAcademico":"Universidad","asignaturaId":"asignatura-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("temario-uuid-123"));
    }

    @Test
    void shouldGetTemarioStatistics() throws Exception {
        given(temarioService.getEstadisticas("profesor@katedra.com"))
                .willReturn(new Katedra.Server.dto.TemarioStatsResponseDTO(7));

        mockMvc.perform(get("/temarios/estadisticas").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llamadasIA").value(7));
    }

    @Test
    void shouldGetHistorial() throws Exception {
        var evento = new Katedra.Server.dto.HistorialEventoResponseDTO(
                "evento-uuid-1", "temario-uuid-123", "Curso de Spring Boot", "Programación",
                Katedra.Server.model.TipoEventoHistorial.CREADO, null, LocalDateTime.now());
        given(historialEventoService.listarHistorial("profesor@katedra.com"))
                .willReturn(List.of(evento));

        mockMvc.perform(get("/temarios/historial").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("evento-uuid-1"))
                .andExpect(jsonPath("$[0].tipo").value("CREADO"));

        verify(historialEventoService).listarHistorial("profesor@katedra.com");
    }

    @Test
    void shouldGetContenidoByTemarioId() throws Exception {
        var contenidoResponse = new Katedra.Server.dto.ContenidoTemarioResponseDTO(
                "contenido-uuid-789", "temario-uuid-123", "## Estructura", "## Teoría",
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
    void shouldGetOriginalSourceByTemarioId() throws Exception {
        var response = new Katedra.Server.dto.ContenidoFuenteResponseDTO(
                "temario-uuid-123", "Texto original extraído");
        given(contenidoTemarioService.getFuenteByTemarioId(
                "temario-uuid-123", "profesor@katedra.com")).willReturn(response);

        mockMvc.perform(get("/temarios/temario-uuid-123/fuente")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temarioId").value("temario-uuid-123"))
                .andExpect(jsonPath("$.contenidoFuente").value("Texto original extraído"));

        verify(contenidoTemarioService).getFuenteByTemarioId(
                "temario-uuid-123", "profesor@katedra.com");
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
                "contenido-uuid-789", "temario-uuid-123", null, null,
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
