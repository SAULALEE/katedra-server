package Katedra.Server.service;

import Katedra.Server.dto.AsignaturaRequestDTO;
import Katedra.Server.model.Asignatura;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.AsignaturaRepository;
import Katedra.Server.repository.TemarioRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AsignaturaServiceTest {

    @Mock
    private AsignaturaRepository asignaturaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private TemarioRepository temarioRepository;

    private AsignaturaService service;
    private Usuario usuario;
    private Asignatura asignatura;

    @BeforeEach
    void setUp() {
        service = new AsignaturaService(asignaturaRepository, usuarioRepository, temarioRepository);
        usuario = new Usuario("profesor@katedra.com", "password", "Profesor", RolUsuario.ROLE_PROFESOR);
        ReflectionTestUtils.setField(usuario, "id", "usuario-1");
        asignatura = new Asignatura(usuario, "Programación", "Fundamentos de programación");
        ReflectionTestUtils.setField(asignatura, "id", "asignatura-1");
    }

    @Test
    void shouldCreateAsignaturaForAuthenticatedUser() {
        given(usuarioRepository.findByEmail(usuario.getEmail())).willReturn(Optional.of(usuario));
        given(asignaturaRepository.save(any(Asignatura.class))).willAnswer(invocation -> {
            Asignatura entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", "asignatura-1");
            return entity;
        });

        var response = service.create(
                usuario.getEmail(),
                new AsignaturaRequestDTO("Programación", "Fundamentos de programación"));

        assertThat(response.id()).isEqualTo("asignatura-1");
        assertThat(response.nombre()).isEqualTo("Programación");
        assertThat(response.descripcion()).isEqualTo("Fundamentos de programación");
        verify(asignaturaRepository).save(any(Asignatura.class));
    }

    @Test
    void shouldListOnlyAuthenticatedUserAsignaturas() {
        given(usuarioRepository.findByEmail(usuario.getEmail())).willReturn(Optional.of(usuario));
        given(asignaturaRepository.findByUsuarioIdOrderByNombreAsc(usuario.getId()))
                .willReturn(List.of(asignatura));

        var response = service.findAll(usuario.getEmail());

        assertThat(response).singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo("asignatura-1"));
    }

    @Test
    void shouldGetOwnedAsignatura() {
        given(asignaturaRepository.findByIdAndUsuarioEmail("asignatura-1", usuario.getEmail()))
                .willReturn(Optional.of(asignatura));

        assertThat(service.findById("asignatura-1", usuario.getEmail()).nombre())
                .isEqualTo("Programación");
    }

    @Test
    void shouldDeleteEmptyAsignatura() {
        given(asignaturaRepository.findByIdAndUsuarioEmail("asignatura-1", usuario.getEmail()))
                .willReturn(Optional.of(asignatura));
        given(temarioRepository.existsByAsignaturaId("asignatura-1")).willReturn(false);

        service.delete("asignatura-1", usuario.getEmail());

        verify(asignaturaRepository).delete(asignatura);
    }

    @Test
    void shouldRejectDeletingAsignaturaWithTemarios() {
        given(asignaturaRepository.findByIdAndUsuarioEmail("asignatura-1", usuario.getEmail()))
                .willReturn(Optional.of(asignatura));
        given(temarioRepository.existsByAsignaturaId("asignatura-1")).willReturn(true);

        assertThatThrownBy(() -> service.delete("asignatura-1", usuario.getEmail()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(409))
                .hasMessageContaining("primero deben eliminarse sus temarios");

        verify(asignaturaRepository, never()).delete(any(Asignatura.class));
    }
}
