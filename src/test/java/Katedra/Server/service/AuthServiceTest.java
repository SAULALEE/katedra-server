package Katedra.Server.service;

import Katedra.Server.dto.AuthLoginRequestDTO;
import Katedra.Server.dto.AuthRegisterRequestDTO;
import Katedra.Server.dto.AuthResponseDTO;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldRegisterNewUserSuccessfully() {
        // Arrange
        AuthRegisterRequestDTO request = new AuthRegisterRequestDTO("test@cueva.com", "fuego123", "Grog");
        given(usuarioRepository.findByEmail(request.email())).willReturn(Optional.empty());
        given(passwordEncoder.encode(request.password())).willReturn("encoded_fuego123");
        given(jwtService.generateToken(any(Usuario.class))).willReturn("mocked_jwt_token");

        // Act
        AuthResponseDTO response = authService.register(request);

        // Assert
        assertThat(response.token()).isEqualTo("mocked_jwt_token");
        assertThat(response.usuario().email()).isEqualTo("test@cueva.com");
        assertThat(response.usuario().nombre()).isEqualTo("Grog");
        assertThat(response.usuario().rol()).isEqualTo(RolUsuario.ROLE_PROFESOR);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        Usuario savedUsuario = usuarioCaptor.getValue();
        assertThat(savedUsuario.getEmail()).isEqualTo("test@cueva.com");
        assertThat(savedUsuario.getPassword()).isEqualTo("encoded_fuego123");
    }

    @Test
    void shouldThrowExceptionWhenRegisteringExistingEmail() {
        // Arrange
        AuthRegisterRequestDTO request = new AuthRegisterRequestDTO("test@cueva.com", "fuego123", "Grog");
        Usuario existingUser = new Usuario("test@cueva.com", "oldpass", "Old Grog", RolUsuario.ROLE_PROFESOR);
        given(usuarioRepository.findByEmail(request.email())).willReturn(Optional.of(existingUser));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authService.register(request);
        });

        assertThat(exception.getMessage()).isEqualTo("El email ya está registrado");
    }

    @Test
    void shouldLoginSuccessfullyAndReturnToken() {
        // Arrange
        AuthLoginRequestDTO request = new AuthLoginRequestDTO("test@cueva.com", "fuego123");
        Usuario usuario = new Usuario("test@cueva.com", "encoded_fuego123", "Grog", RolUsuario.ROLE_PROFESOR);
        
        given(usuarioRepository.findByEmail(request.email())).willReturn(Optional.of(usuario));
        given(jwtService.generateToken(usuario)).willReturn("mocked_jwt_token");

        // Act
        AuthResponseDTO response = authService.login(request);

        // Assert
        assertThat(response.token()).isEqualTo("mocked_jwt_token");
        assertThat(response.usuario().email()).isEqualTo("test@cueva.com");
        
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void shouldThrowExceptionWhenLoginUserNotFound() {
        // Arrange
        AuthLoginRequestDTO request = new AuthLoginRequestDTO("notfound@cueva.com", "fuego123");
        given(usuarioRepository.findByEmail(request.email())).willReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authService.login(request);
        });

        assertThat(exception.getMessage()).isEqualTo("Usuario no encontrado");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }
}
