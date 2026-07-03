package Katedra.Server.service;

import Katedra.Server.dto.AiContenidoDTO;
import Katedra.Server.dto.ContenidoTemarioResponseDTO;
import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.dto.GenerarMaterialRequestDTO;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Temario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.ContenidoTemarioRepository;
import Katedra.Server.repository.TemarioRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContenidoTemarioServiceTest {

    @Mock
    private ContenidoTemarioRepository contenidoTemarioRepository;

    @Mock
    private TemarioRepository temarioRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AiContentGeneratorService aiContentGeneratorService;

    @InjectMocks
    private ContenidoTemarioService contenidoTemarioService;

    private Usuario mockUsuario;
    private Temario mockTemario;
    private AiContenidoDTO mockAiContenido;

    @BeforeEach
    void setUp() {
        mockUsuario = new Usuario("profesor@katedra.com", "securepassword", "Saul", RolUsuario.ROLE_PROFESOR);
        ReflectionTestUtils.setField(mockUsuario, "id", "user-uuid-123");

        mockTemario = new Temario(mockUsuario, "Estructuras de Datos", "Pilas y colas", "Universitario", "Programacion");
        mockTemario.setId("temario-uuid-456");

        mockAiContenido = new AiContenidoDTO(
                "## Teoría generada",
                "## Ejercicios generados",
                List.of(new EvaluacionPreguntaDTO("¿Pregunta?", List.of("A", "B", "C", "D"), 0, "Explicación")),
                List.of(new DiapositivaDTO("Slide 1", List.of("punto1")))
        );
    }

    private ContenidoTemario contenidoConId(Temario temario) {
        ContenidoTemario contenido = new ContenidoTemario(temario);
        contenido.setId("contenido-uuid-789");
        return contenido;
    }

    // --- getContenidoByTemarioId ---

    @Test
    void shouldGetContenidoWhenItExists() {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setTeoria("## Teoría existente");
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));

        ContenidoTemarioResponseDTO response =
                contenidoTemarioService.getContenidoByTemarioId("temario-uuid-456", "profesor@katedra.com");

        assertThat(response.id()).isEqualTo("contenido-uuid-789");
        assertThat(response.teoria()).isEqualTo("## Teoría existente");
    }

    @Test
    void shouldThrowWhenContenidoNotGenerated() {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.getContenidoByTemarioId("temario-uuid-456", "profesor@katedra.com"));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).contains("Contenido no generado");
        verify(contenidoTemarioRepository, never()).save(any(ContenidoTemario.class));
    }

    @Test
    void shouldThrowWhenTemarioNotFound() {
        given(temarioRepository.findById("unknown-id")).willReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.getContenidoByTemarioId("unknown-id", "profesor@katedra.com"));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).isEqualTo("Temario no encontrado");
    }

    @Test
    void shouldThrowWhenAccessDeniedToContenido() {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.getContenidoByTemarioId("temario-uuid-456", "other@katedra.com"));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.getReason()).contains("Acceso denegado");
    }

    // --- generarMaterial ---

    @Test
    void shouldGenerarMaterialWithAiContent() throws ExecutionException, InterruptedException {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.empty());
        given(aiContentGeneratorService.generarContenido("Programacion", "Estructuras de Datos", "Pilas y colas", "Universitario"))
                .willReturn(CompletableFuture.completedFuture(mockAiContenido));
        given(contenidoTemarioRepository.save(any(ContenidoTemario.class)))
                .willAnswer(invocation -> {
                    ContenidoTemario saved = invocation.getArgument(0);
                    saved.setId("contenido-uuid-789");
                    return saved;
                });

        ContenidoTemarioResponseDTO response =
                contenidoTemarioService.generarMaterial("temario-uuid-456", "profesor@katedra.com").get();

        assertThat(response.id()).isEqualTo("contenido-uuid-789");
        assertThat(response.teoria()).isEqualTo("## Teoría generada");
        assertThat(response.ejercicios()).isEqualTo("## Ejercicios generados");
        verify(contenidoTemarioRepository).save(any(ContenidoTemario.class));
    }

    @Test
    void shouldThrowWhenGenerarMaterialAccessDenied() {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.generarMaterial("temario-uuid-456", "other@katedra.com"));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.getReason()).contains("Acceso denegado");
        verify(aiContentGeneratorService, never()).generarContenido(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldReuseExistingContenidoWhenRegenerating() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarContenido(anyString(), anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(mockAiContenido));
        given(contenidoTemarioRepository.save(existing)).willReturn(existing);

        ContenidoTemarioResponseDTO response =
                contenidoTemarioService.generarMaterial("temario-uuid-456", "profesor@katedra.com").get();

        assertThat(response.id()).isEqualTo("contenido-uuid-789");
        assertThat(existing.getTeoria()).isEqualTo("## Teoría generada");
        verify(contenidoTemarioRepository).save(existing);
    }

    // --- generarMaterialDesdeCero ---

    @Test
    void shouldGenerarMaterialDesdeCero() throws ExecutionException, InterruptedException {
        GenerarMaterialRequestDTO request = new GenerarMaterialRequestDTO("Programacion", "Grafos", "Unidad 1: BFS");
        given(usuarioRepository.findByEmail("profesor@katedra.com")).willReturn(Optional.of(mockUsuario));
        given(temarioRepository.save(any(Temario.class))).willAnswer(invocation -> {
            Temario saved = invocation.getArgument(0);
            saved.setId("temario-nuevo-001");
            return saved;
        });
        given(aiContentGeneratorService.generarContenido("Programacion", "Grafos", "Unidad 1: BFS", "Universitario"))
                .willReturn(CompletableFuture.completedFuture(mockAiContenido));
        given(contenidoTemarioRepository.save(any(ContenidoTemario.class)))
                .willAnswer(invocation -> {
                    ContenidoTemario saved = invocation.getArgument(0);
                    saved.setId("contenido-nuevo-001");
                    return saved;
                });

        ContenidoTemarioResponseDTO response =
                contenidoTemarioService.generarMaterialDesdeCero(request, "profesor@katedra.com").get();

        assertThat(response.id()).isEqualTo("contenido-nuevo-001");
        assertThat(response.temarioId()).isEqualTo("temario-nuevo-001");
        assertThat(response.teoria()).isEqualTo("## Teoría generada");
        verify(temarioRepository).save(any(Temario.class));
        verify(contenidoTemarioRepository).save(any(ContenidoTemario.class));
    }

    @Test
    void shouldThrowWhenGenerarDesdeCeroWithUnknownUser() {
        GenerarMaterialRequestDTO request = new GenerarMaterialRequestDTO("Programacion", "Grafos", "Unidad 1");
        given(usuarioRepository.findByEmail("unknown@katedra.com")).willReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.generarMaterialDesdeCero(request, "unknown@katedra.com"));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).isEqualTo("Usuario no encontrado");
        verify(temarioRepository, never()).save(any(Temario.class));
    }
}
