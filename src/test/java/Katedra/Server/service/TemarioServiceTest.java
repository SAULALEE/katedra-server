package Katedra.Server.service;

import Katedra.Server.dto.TemarioRequestDTO;
import Katedra.Server.dto.TemarioResponseDTO;
import Katedra.Server.dto.TemarioDriveRequestDTO;
import Katedra.Server.dto.TemarioUrlRequestDTO;
import Katedra.Server.model.NivelAcademico;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.ContenidoTemario;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TemarioServiceTest {

    @Mock
    private TemarioRepository temarioRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ContenidoTemarioRepository contenidoTemarioRepository;

    @Mock
    private TemarioFileExtractionService temarioFileExtractionService;

    @Mock
    private TemarioUrlExtractionService temarioUrlExtractionService;

    @Mock
    private TemarioGoogleDriveDownloadService temarioGoogleDriveDownloadService;

    @InjectMocks
    private TemarioService temarioService;

    private Usuario mockUsuario;
    private Temario mockTemario;

    @BeforeEach
    void setUp() {
        mockUsuario = new Usuario("profesor@katedra.com", "securepassword", "Saul", RolUsuario.ROLE_PROFESOR);
        ReflectionTestUtils.setField(mockUsuario, "id", "user-uuid-123");

        mockTemario = new Temario(mockUsuario, "Curso de Spring Boot", "Temario completo de Spring Boot", NivelAcademico.UNIVERSITARIO, "Programacion");
        mockTemario.setId("temario-uuid-456");
        mockTemario.setCreatedAt(LocalDateTime.now());
        mockTemario.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void shouldCreateTemarioSuccessfully() {
        // Arrange
        TemarioRequestDTO request = new TemarioRequestDTO(
                "Curso de Spring Boot",
                "Temario completo de Spring Boot",
                NivelAcademico.UNIVERSITARIO,
                "Programacion"
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
        assertThat(response.gradoAcademico()).isEqualTo(NivelAcademico.UNIVERSITARIO);
        assertThat(response.asignatura()).isEqualTo("Programacion");

        verify(usuarioRepository).findByEmail(mockUsuario.getEmail());
        verify(temarioRepository).save(any(Temario.class));
    }

    @Test
    void shouldThrowExceptionWhenCreatingTemarioWithNonExistingUser() {
        // Arrange
        TemarioRequestDTO request = new TemarioRequestDTO(
                "Curso de Spring Boot",
                "Temario completo de Spring Boot",
                NivelAcademico.UNIVERSITARIO,
                "Programacion"
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

        // Act
        temarioService.deleteTemario(mockTemario.getId(), mockUsuario.getEmail());

        // Assert
        verify(temarioRepository).findById(mockTemario.getId());
        verify(temarioRepository).delete(mockTemario);
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
                mockUsuario.getEmail(), file, "Curso de Spring Boot", "Programacion", "universitario");

        assertThat(response.temario().id()).isEqualTo("temario-uuid-456");
        assertThat(response.contenidoId()).isEqualTo("contenido-uuid-789");
        assertThat(response.archivoNombre()).isEqualTo("temario.md");
        assertThat(response.caracteresExtraidos()).isEqualTo(9);
        verify(contenidoTemarioRepository).save(any(ContenidoTemario.class));
    }

    @Test
    void shouldLoadTemarioFromExtractedUrl() {
        var request = new TemarioUrlRequestDTO(
                "https://example.com/temario", "Temario Web", "Programacion", "universitario");
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
        verify(contenidoTemarioRepository).save(any(ContenidoTemario.class));
    }

    @Test
    void shouldLoadTemarioFromPublicDriveLink() {
        var request = new TemarioDriveRequestDTO(
                "https://drive.google.com/file/d/file-123/view", "Temario Drive", "Programacion", "universitario");
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "drive-temario.md", "text/markdown", "# Drive".getBytes());
        var downloaded = new TemarioGoogleDriveDownloadService.DownloadedDriveFile("file-123", file);
        var extracted = new TemarioFileExtractionService.ExtractedTemarioFile(
                "drive-temario.md", "text/markdown", "# Drive");
        given(usuarioRepository.findByEmail(mockUsuario.getEmail())).willReturn(Optional.of(mockUsuario));
        given(temarioGoogleDriveDownloadService.download(request.url())).willReturn(downloaded);
        given(temarioFileExtractionService.extract(file)).willReturn(extracted);
        given(temarioRepository.save(any(Temario.class))).willReturn(mockTemario);
        given(contenidoTemarioRepository.save(any(ContenidoTemario.class))).willAnswer(invocation -> {
            ContenidoTemario saved = invocation.getArgument(0);
            saved.setId("contenido-uuid-791");
            return saved;
        });

        var response = temarioService.cargarTemarioDrive(mockUsuario.getEmail(), request);

        assertThat(response.temario().id()).isEqualTo("temario-uuid-456");
        assertThat(response.contenidoId()).isEqualTo("contenido-uuid-791");
        assertThat(response.archivoNombre()).isEqualTo("drive-temario.md");
        assertThat(response.fuenteUrl()).isEqualTo("https://drive.google.com/file/d/file-123/view");
        assertThat(response.caracteresExtraidos()).isEqualTo(7);
        verify(contenidoTemarioRepository).save(any(ContenidoTemario.class));
    }
}
