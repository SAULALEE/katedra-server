package Katedra.Server.service;

import Katedra.Server.dto.AuthLoginRequestDTO;
import Katedra.Server.dto.AuthRegisterRequestDTO;
import Katedra.Server.dto.AuthResponseDTO;
import Katedra.Server.dto.UsuarioDTO;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AuthenticationManager authenticationManager) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponseDTO register(AuthRegisterRequestDTO request) {
        if (usuarioRepository.findByEmail(request.email()).isPresent()) {
            throw new RuntimeException("El email ya está registrado");
        }

        Usuario usuario = new Usuario(
            request.email(),
            passwordEncoder.encode(request.password()),
            request.nombre(),
            RolUsuario.ROLE_PROFESOR
        );

        usuarioRepository.save(usuario);
        String jwtToken = jwtService.generateToken(usuario);
        return new AuthResponseDTO(jwtToken, mapToDTO(usuario));
    }

    public AuthResponseDTO login(AuthLoginRequestDTO request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        String jwtToken = jwtService.generateToken(usuario);
        return new AuthResponseDTO(jwtToken, mapToDTO(usuario));
    }

    private UsuarioDTO mapToDTO(Usuario usuario) {
        return new UsuarioDTO(usuario.getId(), usuario.getEmail(), usuario.getNombre(), usuario.getRol());
    }
}
