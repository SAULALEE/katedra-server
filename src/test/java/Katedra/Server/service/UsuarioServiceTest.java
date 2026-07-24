package Katedra.Server.service;

import Katedra.Server.dto.UsuarioCreateRequestDTO;
import Katedra.Server.dto.UsuarioCreateResponseDTO;
import Katedra.Server.dto.UsuarioUpdateRequestDTO;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    void shouldCreateAdminWithTemporaryPasswordAndMustChangeFlag() {
        // Arrange
        UsuarioCreateRequestDTO request = new UsuarioCreateRequestDTO("Nuevo Admin", "nuevo@katedra.com");
        given(usuarioRepository.findByEmail("nuevo@katedra.com")).willReturn(Optional.empty());
        given(passwordEncoder.encode(any())).willReturn("encoded_temp");
        given(usuarioRepository.save(any(Usuario.class))).willAnswer(invocation -> invocation.getArgument(0));

        // Act
        UsuarioCreateResponseDTO response = usuarioService.createAdmin(request);

        // Assert
        assertThat(response.temporaryPassword()).hasSize(14);
        assertThat(response.usuario().email()).isEqualTo("nuevo@katedra.com");
        assertThat(response.usuario().rol()).isEqualTo(RolUsuario.ROLE_ADMIN);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().isMustChangePassword()).isTrue();
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded_temp");
    }

    @Test
    void shouldThrowExceptionWhenCreatingAdminWithBlankNombre() {
        // Arrange
        UsuarioCreateRequestDTO request = new UsuarioCreateRequestDTO("  ", "nuevo@katedra.com");

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> usuarioService.createAdmin(request));

        assertThat(exception.getReason()).isEqualTo("El nombre es obligatorio");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenCreatingAdminWithDuplicateEmail() {
        // Arrange
        UsuarioCreateRequestDTO request = new UsuarioCreateRequestDTO("Nuevo Admin", "existente@katedra.com");
        given(usuarioRepository.findByEmail("existente@katedra.com"))
                .willReturn(Optional.of(new Usuario("existente@katedra.com", "x", "Otro", RolUsuario.ROLE_ADMIN)));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> usuarioService.createAdmin(request));

        assertThat(exception.getReason()).isEqualTo("El email ya está registrado");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void shouldUpdateUsuarioSuccessfully() {
        // Arrange
        Usuario existing = new Usuario("old@katedra.com", "x", "Old Name", RolUsuario.ROLE_PROFESOR);
        UsuarioUpdateRequestDTO request = new UsuarioUpdateRequestDTO("New Name", "new@katedra.com", RolUsuario.ROLE_ADMIN);

        given(usuarioRepository.findById("id-1")).willReturn(Optional.of(existing));
        given(usuarioRepository.findByEmail("new@katedra.com")).willReturn(Optional.empty());
        given(usuarioRepository.save(any(Usuario.class))).willAnswer(invocation -> invocation.getArgument(0));

        // Act
        var result = usuarioService.updateUsuario("id-1", request);

        // Assert
        assertThat(result.nombre()).isEqualTo("New Name");
        assertThat(result.email()).isEqualTo("new@katedra.com");
        assertThat(result.rol()).isEqualTo(RolUsuario.ROLE_ADMIN);
    }

    @Test
    void shouldThrowExceptionWhenDeletingOwnAccount() {
        // Arrange
        Usuario usuario = new Usuario("self@katedra.com", "x", "Self", RolUsuario.ROLE_ADMIN);
        given(usuarioRepository.findById("id-1")).willReturn(Optional.of(usuario));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> usuarioService.deleteUsuario("id-1", "self@katedra.com"));

        assertThat(exception.getReason()).isEqualTo("No puedes eliminar tu propia cuenta");
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void shouldThrowExceptionWhenUsuarioNotFound() {
        given(usuarioRepository.findById("missing")).willReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> usuarioService.getUsuarioById("missing"));

        assertThat(exception.getReason()).isEqualTo("Usuario no encontrado");
    }
}
