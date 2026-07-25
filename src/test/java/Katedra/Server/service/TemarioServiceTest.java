package Katedra.Server.service;

import Katedra.Server.dto.TemarioRequestDTO;
import Katedra.Server.dto.TemarioResponseDTO;
import Katedra.Server.dto.TemarioUrlRequestDTO;
import Katedra.Server.model.Asignatura;
import Katedra.Server.model.NivelAcademico;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.model.Temario;
import Katedra.Server.model.TipoEventoHistorial;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.AsignaturaRepository;
import Katedra.Server.repository.ContenidoTemarioRepository;
import Katedra.Server.repository.TemarioRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TemarioServiceTest {

    @Mock
    private TemarioRepository temarioRepository;

    @Mock
    private AsignaturaRepository asignaturaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ContenidoTemarioRepository contenidoTemarioRepository;

    @Mock
    private TemarioFileExtractionService temarioFileExtractionService;

    @Mock
    private TemarioUrlExtractionService temarioUrlExtractionService;

    @Mock
    private HistorialEventoService historialEventoService;

    @InjectMocks
    private TemarioService temarioService;

    private Usuario mockUsuario;
    private Asignatura mockAsignatura;
    private Temario mockTemario;

    @BeforeEach
    void setUp() {
        mockUsuario = new Usuario("profesor@katedra.com", "securepassword", "Saul", RolUsuario.ROLE_PROFESOR);
        ReflectionTestUtils.setField(mockUsuario, "id", "user-uuid-123");

        mockAsignatura = new Asignatura(mockUsuario, "Programacion", "Asignatura de desarrollo");
        ReflectionTestUtils.setField(mockAsignatura, "id", "asignatura-1");
        mockTemario = new Temario(
                mockUsuario,
                "Curso de Spring Boot",
                "Temario completo de Spring Boot",
                "universitario",
                mockAsignatura);
        mockTemario.setId("temario-uuid-456");
        mockTemario.setCreatedAt(LocalDateTime.now());
        mockTemario.setUpdatedAt(LocalDateTime.now());
        org.mockito.Mockito.lenient()
                .when(asignaturaRepository.findByIdAndUsuarioEmail("asignatura-1", mockUsuario.getEmail()))
                .thenReturn(Optional.of(mockAsignatura));
    }

    @Test
    void shouldCreateTemarioSuccessfully() {
        // Arrange
        TemarioRequestDTO request = new TemarioRequestDTO(
                "Curso de Spring Boot",
                "Temario completo de Spring Boot",
                "universitario",
                "asignatura-1"
        );
        given(usuarioRepository.findByEmail(mockUsuario.getEmail())).willReturn(Optional.of(mockUsuario));
        given(temarioRepository.save(any(Temario.class))).willReturn(mockTemario);

        // Act
        TemarioResponseDTO response = temarioService.createTemario(mockUsuario.getEmail(), request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("temario-uuid-456");
        assertThat(response.titulo()).isEqualTo("Curso de Spring Boot");
        assertThat(response.descripcion()).isEqualTo("Temario completo de Spring Boot");
        assertThat(response.gradoAcademico()).isEqualTo("universitario");
        assertThat(response.asignatura()).isEqualTo("Programacion");
        assertThat(response.favorito()).isFalse();

        verify(usuarioRepository).findByEmail(mockUsuario.getEmail());
        verify(temarioRepository).save(any(Temario.class));
        verify(contenidoTemarioRepository).save(argThat(contenido -> contenido.getTemario() == mockTemario));
        verify(historialEventoService).registrar(mockTemario, TipoEventoHistorial.CREADO, null);
    }

    @Test
    void shouldMaintainAsignaturaToTemariosRelationship() {
        assertThat(mockAsignatura.getTemarios()).containsExactly(mockTemario);
        assertThat(mockTemario.getAsignatura()).isSameAs(mockAsignatura);
    }

    @Test
    void shouldRejectMultipleAcademicGradesAtServiceBoundary() {
        TemarioRequestDTO request = new TemarioRequestDTO(
                "Curso", "Contenido", "Primaria, Universidad", "asignatura-1");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> temarioService.createTemario(mockUsuario.getEmail(), request));

        assertThat(exception.getStatusCode().value()).isEqualTo(400);
        assertThat(exception.getReason()).isEqualTo("Solo se permite un grado académico");
        verify(temarioRepository, never()).save(any(Temario.class));
    }

    @Test
    void shouldUpdateOwnedTemarioUsingExistingContract() {
        given(temarioRepository.findById(mockTemario.getId())).willReturn(Optional.of(mockTemario));
        given(temarioRepository.save(mockTemario)).willReturn(mockTemario);
        TemarioRequestDTO request = new TemarioRequestDTO(
                "Spring actualizado", "Nueva descripción", "Posgrado", "asignatura-1");

        TemarioResponseDTO response = temarioService.updateTemario(
                mockTemario.getId(), mockUsuario.getEmail(), request);

        assertThat(response.titulo()).isEqualTo("Spring actualizado");
        assertThat(response.descripcion()).isEqualTo("Nueva descripción");
        assertThat(response.gradoAcademico()).isEqualTo("Posgrado");
        assertThat(response.asignatura()).isEqualTo("Programacion");
        verify(temarioRepository).save(mockTemario);
        verify(historialEventoService).registrar(mockTemario, TipoEventoHistorial.EDITADO, null);
    }

    @Test
    void shouldThrowExceptionWhenCreatingTemarioWithNonExistingUser() {
        // Arrange
        TemarioRequestDTO request = new TemarioRequestDTO(
                "Curso de Spring Boot",
                "Temario completo de Spring Boot",
                "universitario",
                "asignatura-1"
        );
        given(usuarioRepository.findByEmail("unknown@katedra.com")).willReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            temarioService.createTemario("unknown@katedra.com", request);
        });

        assertThat(exception.getMessage()).isEqualTo("Usuario no encontrado");
        verify(usuarioRepository).findByEmail("unknown@katedra.com");
        verify(temarioRepository, never()).save(any(Temario.class));
    }

    @Test
    void shouldGetTemariosByUserSuccessfully() {
        // Arrange
        given(usuarioRepository.findByEmail(mockUsuario.getEmail())).willReturn(Optional.of(mockUsuario));
        given(temarioRepository.findByUsuarioIdOrderByCreatedAtAsc(mockUsuario.getId()))
                .willReturn(List.of(mockTemario));

        // Act
        List<TemarioResponseDTO> list = temarioService.getTemariosByUser(mockUsuario.getEmail());

        // Assert
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().id()).isEqualTo("temario-uuid-456");
        verify(usuarioRepository).findByEmail(mockUsuario.getEmail());
        verify(temarioRepository).findByUsuarioIdOrderByCreatedAtAsc(mockUsuario.getId());
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoTemarios() {
        // Arrange
        given(usuarioRepository.findByEmail(mockUsuario.getEmail())).willReturn(Optional.of(mockUsuario));
        given(temarioRepository.findByUsuarioIdOrderByCreatedAtAsc(mockUsuario.getId()))
                .willReturn(Collections.emptyList());

        // Act
        List<TemarioResponseDTO> list = temarioService.getTemariosByUser(mockUsuario.getEmail());

        // Assert
        assertThat(list).isEmpty();
        verify(usuarioRepository).findByEmail(mockUsuario.getEmail());
        verify(temarioRepository).findByUsuarioIdOrderByCreatedAtAsc(mockUsuario.getId());
    }

    @Test
    void shouldMarkOwnedTemarioAsFavorite() {
        given(temarioRepository.findById(mockTemario.getId())).willReturn(Optional.of(mockTemario));
        given(temarioRepository.save(mockTemario)).willReturn(mockTemario);

        TemarioResponseDTO response = temarioService.updateFavorito(
                mockTemario.getId(), mockUsuario.getEmail(), true);

        assertThat(response.favorito()).isTrue();
        assertThat(mockTemario.isFavorito()).isTrue();
        verify(temarioRepository).save(mockTemario);
        verify(historialEventoService).registrar(mockTemario, TipoEventoHistorial.FAVORITO_AGREGADO, null);
    }

    @Test
    void shouldUnmarkOwnedTemarioAsFavorite() {
        mockTemario.setFavorito(true);
        given(temarioRepository.findById(mockTemario.getId())).willReturn(Optional.of(mockTemario));
        given(temarioRepository.save(mockTemario)).willReturn(mockTemario);

        TemarioResponseDTO response = temarioService.updateFavorito(
                mockTemario.getId(), mockUsuario.getEmail(), false);

        assertThat(response.favorito()).isFalse();
        assertThat(mockTemario.isFavorito()).isFalse();
        verify(historialEventoService).registrar(mockTemario, TipoEventoHistorial.FAVORITO_QUITADO, null);
    }

    @Test
    void shouldListOnlyAuthenticatedUserFavorites() {
        mockTemario.setFavorito(true);
        given(usuarioRepository.findByEmail(mockUsuario.getEmail())).willReturn(Optional.of(mockUsuario));
        given(temarioRepository.findByUsuarioIdAndFavoritoTrueOrderByCreatedAtAsc(mockUsuario.getId()))
                .willReturn(List.of(mockTemario));

        List<TemarioResponseDTO> favoritos =
                temarioService.getFavoritosByUser(mockUsuario.getEmail());

        assertThat(favoritos).singleElement()
                .satisfies(temario -> assertThat(temario.favorito()).isTrue());
        verify(temarioRepository)
                .findByUsuarioIdAndFavoritoTrueOrderByCreatedAtAsc(mockUsuario.getId());
    }

    @Test
    void shouldGetOnlyAuthenticatedUserTemariosForAsignatura() {
        given(usuarioRepository.findByEmail(mockUsuario.getEmail())).willReturn(Optional.of(mockUsuario));
        given(temarioRepository.findByUsuarioIdAndAsignaturaIdOrderByCreatedAtAsc(
                mockUsuario.getId(), "asignatura-1"))
                .willReturn(List.of(mockTemario));

        List<TemarioResponseDTO> temarios = temarioService.getTemariosByAsignatura(
                mockUsuario.getEmail(), "asignatura-1");

        assertThat(temarios).singleElement()
                .satisfies(temario -> assertThat(temario.asignatura()).isEqualTo("Programacion"));
        verify(temarioRepository).findByUsuarioIdAndAsignaturaIdOrderByCreatedAtAsc(
                mockUsuario.getId(), "asignatura-1");
    }

    @Test
    void shouldGetTemarioByIdSuccessfully() {
        // Arrange
        given(temarioRepository.findById(mockTemario.getId())).willReturn(Optional.of(mockTemario));

        // Act
        TemarioResponseDTO response = temarioService.getTemarioById(mockTemario.getId(), mockUsuario.getEmail());

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("temario-uuid-456");
        verify(temarioRepository).findById(mockTemario.getId());
    }

    @Test
    void shouldThrowExceptionWhenTemarioNotFound() {
        // Arrange
        given(temarioRepository.findById("unknown-id")).willReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            temarioService.getTemarioById("unknown-id", mockUsuario.getEmail());
        });

        assertThat(exception.getMessage()).isEqualTo("Temario no encontrado");
        verify(temarioRepository).findById("unknown-id");
    }

    @Test
    void shouldThrowExceptionWhenAccessDeniedToTemario() {
        // Arrange
        given(temarioRepository.findById(mockTemario.getId())).willReturn(Optional.of(mockTemario));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            temarioService.getTemarioById(mockTemario.getId(), "other@katedra.com");
        });

        assertThat(exception.getMessage()).isEqualTo("Acceso denegado a este temario");
        verify(temarioRepository).findById(mockTemario.getId());
    }

    @Test
    void shouldDeleteTemarioSuccessfully() {
        // Arrange
        given(temarioRepository.findById(mockTemario.getId())).willReturn(Optional.of(mockTemario));
        ContenidoTemario contenido = new ContenidoTemario(mockTemario);
        given(contenidoTemarioRepository.findByTemarioId(mockTemario.getId())).willReturn(Optional.of(contenido));

        // Act
        temarioService.deleteTemario(mockTemario.getId(), mockUsuario.getEmail());

        // Assert
        verify(temarioRepository).findById(mockTemario.getId());
        verify(contenidoTemarioRepository).delete(contenido);
        verify(temarioRepository).delete(mockTemario);
        verify(historialEventoService).registrar(mockTemario, TipoEventoHistorial.ELIMINADO, null);
    }

    @Test
    void shouldDeleteFavoriteTemarioWithoutAdditionalFavoriteRecords() {
        mockTemario.setFavorito(true);
        given(temarioRepository.findById(mockTemario.getId())).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId(mockTemario.getId()))
                .willReturn(Optional.empty());

        temarioService.deleteTemario(mockTemario.getId(), mockUsuario.getEmail());

        verify(temarioRepository).delete(mockTemario);
    }

    @Test
    void shouldDeleteTemarioWithoutContentSuccessfully() {
        given(temarioRepository.findById(mockTemario.getId())).willReturn(Optional.of(mockTemario));
        given(contenidoTemarioRepository.findByTemarioId(mockTemario.getId())).willReturn(Optional.empty());

        temarioService.deleteTemario(mockTemario.getId(), mockUsuario.getEmail());

        verify(temarioRepository).delete(mockTemario);
        verify(contenidoTemarioRepository, never()).delete(any(ContenidoTemario.class));
    }

    @Test
    void shouldReturnRealAiGenerationCountForUser() {
        mockUsuario.setAiGenerationCount(7L);
        given(usuarioRepository.findByEmail(mockUsuario.getEmail())).willReturn(Optional.of(mockUsuario));

        assertThat(temarioService.getEstadisticas(mockUsuario.getEmail()).llamadasIA()).isEqualTo(7L);
    }

    @Test
    void shouldThrowExceptionWhenDeletingNonExistingTemario() {
        // Arrange
        given(temarioRepository.findById("unknown-id")).willReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            temarioService.deleteTemario("unknown-id", mockUsuario.getEmail());
        });

        assertThat(exception.getMessage()).isEqualTo("Temario no encontrado");
        verify(temarioRepository).findById("unknown-id");
        verify(temarioRepository, never()).delete(any(Temario.class));
    }

    @Test
    void shouldThrowExceptionWhenDeletingTemarioWithoutPermission() {
        // Arrange
        given(temarioRepository.findById(mockTemario.getId())).willReturn(Optional.of(mockTemario));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            temarioService.deleteTemario(mockTemario.getId(), "other@katedra.com");
        });

        assertThat(exception.getMessage()).isEqualTo("Acceso denegado a este temario");
        verify(temarioRepository).findById(mockTemario.getId());
        verify(temarioRepository, never()).delete(any(Temario.class));
    }

    @Test
    void shouldLoadTemarioFromExtractedFile() {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "temario.md", "text/markdown", "# Temario".getBytes());
        var extracted = new TemarioFileExtractionService.ExtractedTemarioFile(
                "temario.md", "text/markdown", "# Temario");
        given(usuarioRepository.findByEmail(mockUsuario.getEmail())).willReturn(Optional.of(mockUsuario));
        given(temarioFileExtractionService.extract(file)).willReturn(extracted);
        given(temarioRepository.save(any(Temario.class))).willReturn(mockTemario);
        given(contenidoTemarioRepository.save(any(ContenidoTemario.class))).willAnswer(invocation -> {
            ContenidoTemario saved = invocation.getArgument(0);
            saved.setId("contenido-uuid-789");
            return saved;
        });

        var response = temarioService.cargarTemarioArchivo(
                mockUsuario.getEmail(), file, "Curso de Spring Boot", "asignatura-1", "universitario");

        assertThat(response.temario().id()).isEqualTo("temario-uuid-456");
        assertThat(response.contenidoId()).isEqualTo("contenido-uuid-789");
        assertThat(response.archivoNombre()).isEqualTo("temario.md");
        assertThat(response.caracteresExtraidos()).isEqualTo(9);
        verify(contenidoTemarioRepository).save(argThat(contenido ->
                "# Temario".equals(contenido.getTeoria())
                        && "# Temario".equals(contenido.getContenidoFuente())));
    }

    @Test
    void shouldRejectCommaSeparatedAcademicGradesWhenLoadingFile() {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "temario.md", "text/markdown", "# Temario".getBytes());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> temarioService.cargarTemarioArchivo(
                        mockUsuario.getEmail(), file, "Curso", "asignatura-1",
                        "Primaria, Universidad, Diplomado de programacion"));

        assertThat(exception.getReason()).isEqualTo("Solo se permite un grado académico");
        verify(temarioFileExtractionService, never()).extract(file);
    }

    @Test
    void shouldLoadTemarioFromExtractedUrl() {
        var request = new TemarioUrlRequestDTO(
                "https://example.com/temario", "Temario Web", "asignatura-1", "universitario");
        var extracted = new TemarioUrlExtractionService.ExtractedTemarioUrl(
                "https://example.com/temario", "Example Title", "Contenido web limpio");
        given(usuarioRepository.findByEmail(mockUsuario.getEmail())).willReturn(Optional.of(mockUsuario));
        given(temarioUrlExtractionService.extract(request.url())).willReturn(extracted);
        given(temarioRepository.save(any(Temario.class))).willReturn(mockTemario);
        given(contenidoTemarioRepository.save(any(ContenidoTemario.class))).willAnswer(invocation -> {
            ContenidoTemario saved = invocation.getArgument(0);
            saved.setId("contenido-uuid-790");
            return saved;
        });

        var response = temarioService.cargarTemarioUrl(mockUsuario.getEmail(), request);

        assertThat(response.temario().id()).isEqualTo("temario-uuid-456");
        assertThat(response.contenidoId()).isEqualTo("contenido-uuid-790");
        assertThat(response.fuenteUrl()).isEqualTo("https://example.com/temario");
        assertThat(response.archivoNombre()).isNull();
        assertThat(response.caracteresExtraidos()).isEqualTo(20);
        verify(contenidoTemarioRepository).save(argThat(contenido ->
                "Contenido web limpio".equals(contenido.getTeoria())
                        && "Contenido web limpio".equals(contenido.getContenidoFuente())));
    }

}
