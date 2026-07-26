package Katedra.Server.service;

import Katedra.Server.dto.HistorialEventoResponseDTO;
import Katedra.Server.model.Asignatura;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Temario;
import Katedra.Server.model.TipoEventoHistorial;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.HistorialEventoRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HistorialEventoServiceTest {

    @Mock
    private HistorialEventoRepository historialEventoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private HistorialEventoService historialEventoService;

    private Usuario mockUsuario;
    private Temario mockTemario;

    @BeforeEach
    void setUp() {
        mockUsuario = new Usuario("profesor@katedra.com", "securepassword", "Saul", RolUsuario.ROLE_PROFESOR);
        ReflectionTestUtils.setField(mockUsuario, "id", "user-uuid-123");

        Asignatura asignatura = new Asignatura(mockUsuario, "Programacion", "Asignatura de desarrollo");
        ReflectionTestUtils.setField(asignatura, "id", "asignatura-1");

        mockTemario = new Temario(mockUsuario, "Curso de Spring Boot", "Temario", "universitario", asignatura);
        mockTemario.setId("temario-uuid-456");
    }

    @Test
    void shouldPersistASnapshotOfTheTemarioAtEventTime() {
        historialEventoService.registrar(mockTemario, TipoEventoHistorial.CREADO, null);

        ArgumentCaptor<Katedra.Server.model.HistorialEvento> captor =
                ArgumentCaptor.forClass(Katedra.Server.model.HistorialEvento.class);
        verify(historialEventoRepository).save(captor.capture());

        Katedra.Server.model.HistorialEvento evento = captor.getValue();
        assertThat(evento.getUsuarioId()).isEqualTo("user-uuid-123");
        assertThat(evento.getTemarioId()).isEqualTo("temario-uuid-456");
        assertThat(evento.getTemarioTitulo()).isEqualTo("Curso de Spring Boot");
        assertThat(evento.getAsignaturaNombre()).isEqualTo("Programacion");
        assertThat(evento.getTipo()).isEqualTo(TipoEventoHistorial.CREADO);
        assertThat(evento.getDetalle()).isNull();
    }

    @Test
    void shouldPersistDetalleWhenProvided() {
        historialEventoService.registrar(mockTemario, TipoEventoHistorial.GENERADO, "teoria, evaluacion · FLASH");

        ArgumentCaptor<Katedra.Server.model.HistorialEvento> captor =
                ArgumentCaptor.forClass(Katedra.Server.model.HistorialEvento.class);
        verify(historialEventoRepository).save(captor.capture());

        assertThat(captor.getValue().getDetalle()).isEqualTo("teoria, evaluacion · FLASH");
    }

    @Test
    void shouldListHistoryNewestFirstForTheAuthenticatedUser() {
        Katedra.Server.model.HistorialEvento evento = new Katedra.Server.model.HistorialEvento(
                "user-uuid-123", "temario-uuid-456", "Curso de Spring Boot", "Programacion",
                TipoEventoHistorial.CREADO, null);
        given(usuarioRepository.findByEmail(mockUsuario.getEmail())).willReturn(Optional.of(mockUsuario));
        given(historialEventoRepository.findByUsuarioIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .willReturn(List.of(evento));

        List<HistorialEventoResponseDTO> historial = historialEventoService.listarHistorial(mockUsuario.getEmail());

        assertThat(historial).singleElement().satisfies(dto -> {
            assertThat(dto.temarioId()).isEqualTo("temario-uuid-456");
            assertThat(dto.tipo()).isEqualTo(TipoEventoHistorial.CREADO);
        });
        verify(historialEventoRepository).findByUsuarioIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq("user-uuid-123"), any(Pageable.class));
    }

    @Test
    void shouldThrowWhenListingHistoryForUnknownUser() {
        given(usuarioRepository.findByEmail("unknown@katedra.com")).willReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> historialEventoService.listarHistorial("unknown@katedra.com"));

        assertThat(exception.getMessage()).isEqualTo("Usuario no encontrado");
    }
}
