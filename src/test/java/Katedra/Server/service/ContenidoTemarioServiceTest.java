package Katedra.Server.service;

import Katedra.Server.dto.ContenidoTemarioResponseDTO;
import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.dto.GenerarMaterialRequestDTO;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.PiezaMaterial;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Temario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.ContenidoTemarioRepository;
import Katedra.Server.repository.TemarioRepository;
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
import java.util.Set;
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
    private AiContentGeneratorService aiContentGeneratorService;

    @InjectMocks
    private ContenidoTemarioService contenidoTemarioService;

    private Usuario mockUsuario;
    private Temario mockTemario;
    private List<EvaluacionPreguntaDTO> mockEvaluacion;
    private List<DiapositivaDTO> mockDiapositivas;

    @BeforeEach
    void setUp() {
        mockUsuario = new Usuario("profesor@katedra.com", "securepassword", "Saul", RolUsuario.ROLE_PROFESOR);
        ReflectionTestUtils.setField(mockUsuario, "id", "user-uuid-123");

        mockTemario = new Temario(mockUsuario, "Estructuras de Datos", "Pilas y colas", "Universitario", "Programacion");
        mockTemario.setId("temario-uuid-456");

        mockEvaluacion = List.of(new EvaluacionPreguntaDTO("¿Pregunta?", List.of("A", "B", "C", "D"), 0, "Explicación"));
        mockDiapositivas = List.of(new DiapositivaDTO("Slide 1", List.of("punto1")));
    }

    private ContenidoTemario contenidoConId(Temario temario) {
        ContenidoTemario contenido = new ContenidoTemario(temario);
        contenido.setId("contenido-uuid-789");
        return contenido;
    }

    private void stubSaveEchoingWithId() {
        given(contenidoTemarioRepository.save(any(ContenidoTemario.class)))
                .willAnswer(invocation -> {
                    ContenidoTemario saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId("contenido-uuid-789");
                    }
                    return saved;
                });
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
        assertThat(response.piezasOmitidas()).isEmpty();
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

    // --- generarMaterial: validación de request ---

    @Test
    void shouldThrowWhenNoPiezasSelected() {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        GenerarMaterialRequestDTO request = new GenerarMaterialRequestDTO(Set.of(), null, null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.generarMaterial("temario-uuid-456", "profesor@katedra.com", request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(aiContentGeneratorService, never())
                .generarTeoria(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldThrowWhenGenerarMaterialAccessDenied() {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.TEORIA), null, null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.generarMaterial("temario-uuid-456", "other@katedra.com", request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(aiContentGeneratorService, never())
                .generarTeoria(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    // --- generarMaterial: generación selectiva ---

    @Test
    void shouldGenerateOnlyRequestedPiece() throws ExecutionException, InterruptedException {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.empty());
        given(aiContentGeneratorService.generarEvaluacion(anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(mockEvaluacion));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.EVALUACION), ModeloIA.SENCILLO, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.evaluacion()).isEqualTo(mockEvaluacion);
        assertThat(response.teoria()).isNull();
        assertThat(response.ejercicios()).isNull();
        assertThat(response.diapositivas()).isNull();
        assertThat(response.modelo()).isEqualTo("gpt-4o-mini");
        assertThat(response.piezasOmitidas()).isEmpty();

        verify(aiContentGeneratorService, never())
                .generarTeoria(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(aiContentGeneratorService, never())
                .generarEjercicios(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(aiContentGeneratorService, never())
                .generarDiapositivas(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldSkipExistingPieceWithoutRegenerateFlag() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setEvaluacion(mockEvaluacion);
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.EVALUACION), ModeloIA.SENCILLO, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.piezasOmitidas()).containsExactly("evaluacion");
        assertThat(response.evaluacion()).isEqualTo(mockEvaluacion);
        verify(aiContentGeneratorService, never())
                .generarEvaluacion(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(contenidoTemarioRepository, never()).save(any(ContenidoTemario.class));
    }

    @Test
    void shouldRegenerateExistingPieceWhenForced() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setEvaluacion(List.of(new EvaluacionPreguntaDTO("¿Vieja?", List.of("A", "B", "C", "D"), 1, "Vieja")));
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarEvaluacion(anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(mockEvaluacion));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request = new GenerarMaterialRequestDTO(
                Set.of(PiezaMaterial.EVALUACION), ModeloIA.AVANZADO, Set.of(PiezaMaterial.EVALUACION));

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.evaluacion()).isEqualTo(mockEvaluacion);
        assertThat(response.modelo()).isEqualTo("gpt-4o");
        assertThat(response.piezasOmitidas()).isEmpty();
        verify(aiContentGeneratorService)
                .generarEvaluacion(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(contenidoTemarioRepository).save(existing);
    }

    @Test
    void shouldPreserveExistingPiecesWhenGeneratingOthers() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setEvaluacion(mockEvaluacion);
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarTeoria(anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture("## Teoría generada"));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.TEORIA), ModeloIA.SENCILLO, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.teoria()).isEqualTo("## Teoría generada");
        assertThat(response.evaluacion()).isEqualTo(mockEvaluacion);
        verify(aiContentGeneratorService, never())
                .generarEvaluacion(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldGenerateAllPiecesInParallel() throws ExecutionException, InterruptedException {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.empty());
        given(aiContentGeneratorService.generarTeoria(anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture("## Teoría generada"));
        given(aiContentGeneratorService.generarEjercicios(anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture("## Ejercicios generados"));
        given(aiContentGeneratorService.generarEvaluacion(anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(mockEvaluacion));
        given(aiContentGeneratorService.generarDiapositivas(anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(mockDiapositivas));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request = new GenerarMaterialRequestDTO(
                Set.of(PiezaMaterial.TEORIA, PiezaMaterial.EJERCICIOS, PiezaMaterial.EVALUACION, PiezaMaterial.DIAPOSITIVAS),
                null, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.teoria()).isEqualTo("## Teoría generada");
        assertThat(response.ejercicios()).isEqualTo("## Ejercicios generados");
        assertThat(response.evaluacion()).isEqualTo(mockEvaluacion);
        assertThat(response.diapositivas()).isEqualTo(mockDiapositivas);
        // null modelo defaults to SENCILLO
        assertThat(response.modelo()).isEqualTo("gpt-4o-mini");
    }
}
