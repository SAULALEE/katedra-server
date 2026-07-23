package Katedra.Server.service;

import Katedra.Server.dto.ContenidoTemarioResponseDTO;
import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.dto.GenerarMaterialRequestDTO;
import Katedra.Server.model.Asignatura;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.NivelAcademico;
import Katedra.Server.model.PiezaMaterial;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private List<EvaluacionPreguntaDTO> mockEvaluacion;
    private List<DiapositivaDTO> mockDiapositivas;

    @BeforeEach
    void setUp() {
        mockUsuario = new Usuario("profesor@katedra.com", "securepassword", "Saul", RolUsuario.ROLE_PROFESOR);
        ReflectionTestUtils.setField(mockUsuario, "id", "user-uuid-123");

        Asignatura asignatura = new Asignatura(mockUsuario, "Programacion", null);
        ReflectionTestUtils.setField(asignatura, "id", "asignatura-1");
        mockTemario = new Temario(
                mockUsuario, "Estructuras de Datos", "Pilas y colas", "universitario", asignatura);
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

    @Test
    void shouldReturnPersistedOriginalSourceForFileOrUrl() {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setContenidoFuente("Texto original extraído");
        existing.setTeoria("## Teoría docente generada");
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456"))
                .willReturn(Optional.of(existing));

        var response = contenidoTemarioService.getFuenteByTemarioId(
                "temario-uuid-456", "profesor@katedra.com");

        assertThat(response.temarioId()).isEqualTo("temario-uuid-456");
        assertThat(response.contenidoFuente()).isEqualTo("Texto original extraído");
    }

    @Test
    void shouldReturnGeneratedSyllabusAsManualSource() {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setTeoria("## Temario manual generado por IA");
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456"))
                .willReturn(Optional.of(existing));

        var response = contenidoTemarioService.getFuenteByTemarioId(
                "temario-uuid-456", "profesor@katedra.com");

        assertThat(response.contenidoFuente()).isEqualTo("## Temario manual generado por IA");
    }

    @Test
    void shouldRejectSourceAccessFromAnotherUser() {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.getFuenteByTemarioId(
                        "temario-uuid-456", "other@katedra.com"));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(contenidoTemarioRepository, never()).findByTemarioId(anyString());
    }

    // --- generarMaterial: validación de request ---

    @Test
    void shouldThrowWhenNoPiezasSelected() {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        GenerarMaterialRequestDTO request = new GenerarMaterialRequestDTO(Set.of(), null, null, null, null, null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.generarMaterial("temario-uuid-456", "profesor@katedra.com", request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(aiContentGeneratorService, never())
                .generarTeoria(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt());
    }

    @Test
    void shouldThrowWhenGenerarMaterialAccessDenied() {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.TEORIA), null, null, null, null, null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.generarMaterial("temario-uuid-456", "other@katedra.com", request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(aiContentGeneratorService, never())
                .generarTeoria(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt());
    }

    // --- generarMaterial: generación selectiva ---

    @Test
    void shouldGenerateOnlyRequestedPiece() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setTeoria("## Teoría existente");
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarEvaluacion(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt()))
                .willReturn(CompletableFuture.completedFuture(mockEvaluacion));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.EVALUACION), ModeloIA.FLASH, null, null, null, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.evaluacion()).isEqualTo(mockEvaluacion);
        assertThat(response.teoria()).isEqualTo("## Teoría existente");
        assertThat(response.diapositivas()).isNull();
        assertThat(response.modelo()).isEqualTo("flash");
        verify(usuarioRepository).incrementAiGenerationCount(mockUsuario.getId(), 1L);

        verify(aiContentGeneratorService, never())
                .generarTeoria(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt());
        verify(aiContentGeneratorService, never())
                .generarDiapositivas(anyString(), anyString(), anyString(), anyString(), any(ModeloIA.class), anyInt());
    }

    @Test
    void shouldUseIngestedTextAsSourceWhenGeneratingTheory() throws Exception {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setTeoria("Texto extraido del archivo sobre pilas y colas");
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarTeoria(
                anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt()))
                .willReturn(CompletableFuture.completedFuture("## Teoria generada"));
        stubSaveEchoingWithId();

        contenidoTemarioService.generarMaterial(
                "temario-uuid-456", "profesor@katedra.com",
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.TEORIA), ModeloIA.FLASH, null, null, null, null)).get();

        verify(aiContentGeneratorService).generarTeoria(
                eq("Programacion"), eq("Estructuras de Datos"), eq(NivelAcademico.UNIVERSITARIO),
                eq("Texto extraido del archivo sobre pilas y colas"), eq(ModeloIA.FLASH), anyInt());
    }

    @Test
    void shouldRegenerateExistingPieceEvenWithoutForceFlag() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setTeoria("## Teoría existente");
        existing.setEvaluacion(List.of(new EvaluacionPreguntaDTO("¿Vieja?", List.of("A", "B", "C", "D"), 1, "Vieja")));
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarEvaluacion(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt()))
                .willReturn(CompletableFuture.completedFuture(mockEvaluacion));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.EVALUACION), ModeloIA.PRO, null, null, null, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.evaluacion()).isEqualTo(mockEvaluacion);
        assertThat(response.modelo()).isEqualTo("pro");
        verify(aiContentGeneratorService)
                .generarEvaluacion(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt());
        verify(contenidoTemarioRepository).save(existing);
        verify(usuarioRepository).incrementAiGenerationCount(mockUsuario.getId(), 1L);
    }

    // --- generarMaterial: fundamentación en teoría ---

    @Test
    void shouldThrowWhenEvaluacionRequestedWithoutTheory() {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.empty());

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.EVALUACION), ModeloIA.FLASH, null, null, null, null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.generarMaterial("temario-uuid-456", "profesor@katedra.com", request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).contains("Genera la teoría primero");
    }

    @Test
    void shouldThrowWhenDiapositivasRequestedWithoutTheory() {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.empty());

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.DIAPOSITIVAS), ModeloIA.FLASH, null, null, null, null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.generarMaterial("temario-uuid-456", "profesor@katedra.com", request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).contains("Genera la teoría primero");
        verify(aiContentGeneratorService, never())
                .generarDiapositivas(anyString(), anyString(), anyString(), anyString(), any(ModeloIA.class), anyInt());
    }

    @Test
    void shouldGroundDiapositivasInFreshlyGeneratedTeoriaWhenRequestedTogether() throws ExecutionException, InterruptedException {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.empty());
        given(aiContentGeneratorService.generarTeoria(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt()))
                .willReturn(CompletableFuture.completedFuture("## Teoría recién generada"));
        given(aiContentGeneratorService.generarDiapositivas(
                anyString(), anyString(), anyString(), eq("## Teoría recién generada"), any(ModeloIA.class), anyInt()))
                .willReturn(CompletableFuture.completedFuture(mockDiapositivas));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request = new GenerarMaterialRequestDTO(
                Set.of(PiezaMaterial.TEORIA, PiezaMaterial.DIAPOSITIVAS), ModeloIA.FLASH, null, null, null, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.teoria()).isEqualTo("## Teoría recién generada");
        assertThat(response.diapositivas()).isEqualTo(mockDiapositivas);
        // Theory must only be generated once and its result reused for grounding.
        verify(aiContentGeneratorService, org.mockito.Mockito.times(1))
                .generarTeoria(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt());
    }

    @Test
    void shouldGroundEvaluacionInStoredTeoriaWhenNotRegeneratedInThisRequest() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setTeoria("## Teoría ya guardada");
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarEvaluacion(
                anyString(), anyString(), any(NivelAcademico.class), eq("## Teoría ya guardada"), any(ModeloIA.class), anyInt()))
                .willReturn(CompletableFuture.completedFuture(mockEvaluacion));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.EVALUACION), ModeloIA.FLASH, null, null, null, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.evaluacion()).isEqualTo(mockEvaluacion);
        verify(aiContentGeneratorService, never())
                .generarTeoria(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt());
    }

    @Test
    void shouldPreserveExistingPiecesWhenGeneratingOthers() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setEvaluacion(mockEvaluacion);
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarTeoria(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt()))
                .willReturn(CompletableFuture.completedFuture("## Teoría generada"));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.TEORIA), ModeloIA.FLASH, null, null, null, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.teoria()).isEqualTo("## Teoría generada");
        assertThat(response.evaluacion()).isEqualTo(mockEvaluacion);
        verify(aiContentGeneratorService, never())
                .generarEvaluacion(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt());
    }

    @Test
    void shouldGenerateAllPieces() throws ExecutionException, InterruptedException {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.empty());
        given(aiContentGeneratorService.generarTeoria(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt()))
                .willReturn(CompletableFuture.completedFuture("## Teoría generada"));
        given(aiContentGeneratorService.generarEvaluacion(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt()))
                .willReturn(CompletableFuture.completedFuture(mockEvaluacion));
        given(aiContentGeneratorService.generarDiapositivas(anyString(), anyString(), anyString(), anyString(), any(ModeloIA.class), anyInt()))
                .willReturn(CompletableFuture.completedFuture(mockDiapositivas));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request = new GenerarMaterialRequestDTO(
                Set.of(PiezaMaterial.TEORIA, PiezaMaterial.EVALUACION, PiezaMaterial.DIAPOSITIVAS),
                null, null, null, null, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.teoria()).isEqualTo("## Teoría generada");
        assertThat(response.evaluacion()).isEqualTo(mockEvaluacion);
        assertThat(response.diapositivas()).isEqualTo(mockDiapositivas);
        // null modelo defaults to FLASH
        assertThat(response.modelo()).isEqualTo("flash");
        verify(usuarioRepository).incrementAiGenerationCount(mockUsuario.getId(), 3L);
    }

    // --- generarMaterial: fallos por pieza ---

    @Test
    void shouldReportFailureWithoutDiscardingExistingContentWhenPieceFails() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setTeoria("## Teoría generada con Tutor");
        existing.setModelo("flash");
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarTeoria(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt()))
                .willReturn(CompletableFuture.failedFuture(new RuntimeException("rate limit exceeded")));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.TEORIA), ModeloIA.PRO, null, null, null, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        // Old content survives a failed regeneration attempt instead of being wiped.
        assertThat(response.teoria()).isEqualTo("## Teoría generada con Tutor");
        // modelo is NOT advanced to "pro" since nothing actually generated with it,
        // otherwise the response would misleadingly claim Catedrático produced this text.
        assertThat(response.modelo()).isEqualTo("flash");
        assertThat(response.piezasFallidas()).containsEntry("teoria", "rate limit exceeded");
    }

    @Test
    void shouldReturn200WithPiezasFallidasWhenEveryPieceFails() throws ExecutionException, InterruptedException {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.empty());
        given(aiContentGeneratorService.generarTeoria(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt()))
                .willReturn(CompletableFuture.failedFuture(new RuntimeException("model overloaded")));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.TEORIA), ModeloIA.PRO, null, null, null, null);

        // Never throws: AI provider failures are reported per piece, not as an HTTP error.
        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.teoria()).isNull();
        assertThat(response.modelo()).isNull();
        assertThat(response.piezasFallidas()).containsEntry("teoria", "model overloaded");
        verify(usuarioRepository, never()).incrementAiGenerationCount(anyString(), anyLong());
    }

    // --- generarMaterial: numeroDiapositivas ---

    @Test
    void shouldUseTierDefaultWhenNumeroDiapositivasIsNull() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setTeoria("## Teoría existente");
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarDiapositivas(
                anyString(), anyString(), anyString(), anyString(), any(ModeloIA.class), eq(ModeloIA.FLASH.getDefaultDiapositivas())))
                .willReturn(CompletableFuture.completedFuture(mockDiapositivas));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.DIAPOSITIVAS), ModeloIA.FLASH, null, null, null, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.diapositivas()).isEqualTo(mockDiapositivas);
        verify(aiContentGeneratorService).generarDiapositivas(
                anyString(), anyString(), anyString(), anyString(), any(ModeloIA.class), eq(ModeloIA.FLASH.getDefaultDiapositivas()));
    }

    @Test
    void shouldPassThroughExplicitNumeroDiapositivasMatchingTierCount() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setTeoria("## Teoría existente");
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarDiapositivas(
                anyString(), anyString(), anyString(), anyString(), any(ModeloIA.class), eq(8)))
                .willReturn(CompletableFuture.completedFuture(mockDiapositivas));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.DIAPOSITIVAS), ModeloIA.FLASH, null, 8, null, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.diapositivas()).isEqualTo(mockDiapositivas);
        verify(aiContentGeneratorService).generarDiapositivas(
                anyString(), anyString(), anyString(), anyString(), any(ModeloIA.class), eq(8));
    }

    @Test
    void shouldThrowWhenNumeroDiapositivasDiffersFromTierCount() {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.DIAPOSITIVAS), ModeloIA.FLASH, null, 12, null, null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.generarMaterial("temario-uuid-456", "profesor@katedra.com", request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(aiContentGeneratorService, never())
                .generarDiapositivas(anyString(), anyString(), anyString(), anyString(), any(ModeloIA.class), anyInt());
    }

    @Test
    void shouldAllowNumeroDiapositivasMatchingMaxTierCount() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setTeoria("## Teoría existente");
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarDiapositivas(
                anyString(), anyString(), anyString(), anyString(), any(ModeloIA.class), eq(20)))
                .willReturn(CompletableFuture.completedFuture(mockDiapositivas));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.DIAPOSITIVAS), ModeloIA.PRO, null, 20, null, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.diapositivas()).isEqualTo(mockDiapositivas);
    }

    // --- generarMaterial: numeroParrafos ---

    @Test
    void shouldUseTierDefaultWhenNumeroParrafosIsNull() throws ExecutionException, InterruptedException {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.empty());
        given(aiContentGeneratorService.generarTeoria(
                anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class),
                eq(ModeloIA.FLASH.getDefaultParrafosTeoria())))
                .willReturn(CompletableFuture.completedFuture("## Teoría generada"));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.TEORIA), ModeloIA.FLASH, null, null, null, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.teoria()).isEqualTo("## Teoría generada");
        verify(aiContentGeneratorService).generarTeoria(
                anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class),
                eq(ModeloIA.FLASH.getDefaultParrafosTeoria()));
    }

    @Test
    void shouldPassThroughExplicitNumeroParrafosWithinTierRange() throws ExecutionException, InterruptedException {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.empty());
        given(aiContentGeneratorService.generarTeoria(
                anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), eq(30)))
                .willReturn(CompletableFuture.completedFuture("## Teoría generada"));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.TEORIA), ModeloIA.PRO, null, null, 30, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.teoria()).isEqualTo("## Teoría generada");
        verify(aiContentGeneratorService).generarTeoria(
                anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), eq(30));
    }

    @Test
    void shouldThrowWhenNumeroParrafosOutOfTierRange() {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.TEORIA), ModeloIA.FLASH, null, null, 40, null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.generarMaterial("temario-uuid-456", "profesor@katedra.com", request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(aiContentGeneratorService, never())
                .generarTeoria(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt());
    }

    // --- generarMaterial: numeroPreguntas ---

    @Test
    void shouldUseTierDefaultWhenNumeroPreguntasIsNull() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setTeoria("## Teoría existente");
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarEvaluacion(
                anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class),
                eq(ModeloIA.FLASH.getDefaultPreguntas())))
                .willReturn(CompletableFuture.completedFuture(mockEvaluacion));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.EVALUACION), ModeloIA.FLASH, null, null, null, null);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.evaluacion()).isEqualTo(mockEvaluacion);
        verify(aiContentGeneratorService).generarEvaluacion(
                anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class),
                eq(ModeloIA.FLASH.getDefaultPreguntas()));
    }

    @Test
    void shouldPassThroughExplicitNumeroPreguntasWithinTierRange() throws ExecutionException, InterruptedException {
        ContenidoTemario existing = contenidoConId(mockTemario);
        existing.setTeoria("## Teoría existente");
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId("temario-uuid-456")).willReturn(Optional.of(existing));
        given(aiContentGeneratorService.generarEvaluacion(
                anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), eq(25)))
                .willReturn(CompletableFuture.completedFuture(mockEvaluacion));
        stubSaveEchoingWithId();

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.EVALUACION), ModeloIA.PRO, null, null, null, 25);

        ContenidoTemarioResponseDTO response = contenidoTemarioService
                .generarMaterial("temario-uuid-456", "profesor@katedra.com", request).get();

        assertThat(response.evaluacion()).isEqualTo(mockEvaluacion);
        verify(aiContentGeneratorService).generarEvaluacion(
                anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), eq(25));
    }

    @Test
    void shouldThrowWhenNumeroPreguntasOutOfTierRange() {
        given(temarioRepository.findById("temario-uuid-456")).willReturn(Optional.of(mockTemario));

        GenerarMaterialRequestDTO request =
                new GenerarMaterialRequestDTO(Set.of(PiezaMaterial.EVALUACION), ModeloIA.FLASH, null, null, null, 15);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                contenidoTemarioService.generarMaterial("temario-uuid-456", "profesor@katedra.com", request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(aiContentGeneratorService, never())
                .generarEvaluacion(anyString(), anyString(), any(NivelAcademico.class), anyString(), any(ModeloIA.class), anyInt());
    }
}
