package Katedra.Server.service;

import Katedra.Server.dto.AuthLoginRequestDTO;
import Katedra.Server.dto.AuthRegisterRequestDTO;
import Katedra.Server.dto.AuthResponseDTO;
import Katedra.Server.dto.PasswordChangeRequestDTO;
import Katedra.Server.dto.UsuarioDTO;
import Katedra.Server.model.AuthProvider;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

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
            resolveRoleByEmail(request.email())
        );

        usuarioRepository.save(usuario);
        String jwtToken = jwtService.generateToken(usuario);
        return new AuthResponseDTO(jwtToken, mapToDTO(usuario), usuario.isMustChangePassword());
    }

    public AuthResponseDTO login(AuthLoginRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (usuario.getPassword() == null || usuario.getAuthProvider() != AuthProvider.LOCAL) {
            throw new RuntimeException("Esta cuenta usa login social");
        }

        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        String jwtToken = jwtService.generateToken(usuario);
        return new AuthResponseDTO(jwtToken, mapToDTO(usuario), usuario.isMustChangePassword());
    }

    /**
     * Changes the password of the currently authenticated user, clearing the
     * mustChangePassword flag so admin-created accounts stop being blocked
     * after they pick their own password.
     */
    public AuthResponseDTO changePassword(String email, PasswordChangeRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        if (usuario.getPassword() == null || usuario.getAuthProvider() != AuthProvider.LOCAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta cuenta usa login social");
        }
        if (!passwordEncoder.matches(request.currentPassword(), usuario.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual es incorrecta");
        }

        usuario.setPassword(passwordEncoder.encode(request.newPassword()));
        usuario.setMustChangePassword(false);
        usuario.setUpdatedAt(LocalDateTime.now());
        usuarioRepository.save(usuario);

        String jwtToken = jwtService.generateToken(usuario);
        return new AuthResponseDTO(jwtToken, mapToDTO(usuario), false);
    }

    public AuthResponseDTO loginOrRegisterSocial(
            AuthProvider authProvider,
            String providerUserId,
            String email,
            String nombre
    ) {
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new RuntimeException("No se pudo obtener el identificador del proveedor");
        }
        if (email == null || email.isBlank()) {
            throw new RuntimeException("No se pudo obtener el email del proveedor");
        }

        Usuario usuario = usuarioRepository.findByAuthProviderAndProviderUserId(authProvider, providerUserId)
                .orElseGet(() -> usuarioRepository.findByEmail(email)
                        .map(existingUser -> linkSocialAccount(existingUser, authProvider, providerUserId, nombre))
                        .orElseGet(() -> createSocialUser(authProvider, providerUserId, email, nombre)));

        String jwtToken = jwtService.generateToken(usuario);
        return new AuthResponseDTO(jwtToken, mapToDTO(usuario), usuario.isMustChangePassword());
    }

    private Usuario linkSocialAccount(
            Usuario usuario,
            AuthProvider authProvider,
            String providerUserId,
            String nombre
    ) {
        if (usuario.getAuthProvider() == AuthProvider.LOCAL && usuario.getPassword() != null) {
            throw new RuntimeException("El email ya está registrado con login tradicional");
        }
        if (usuario.getAuthProvider() != AuthProvider.LOCAL && usuario.getAuthProvider() != authProvider) {
            throw new RuntimeException("El email ya está vinculado a otro proveedor");
        }

        usuario.setAuthProvider(authProvider);
        usuario.setProviderUserId(providerUserId);
        if (usuario.getNombre() == null || usuario.getNombre().isBlank()) {
            usuario.setNombre(nombre);
        }
        return usuarioRepository.save(usuario);
    }

    private Usuario createSocialUser(
            AuthProvider authProvider,
            String providerUserId,
            String email,
            String nombre
    ) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setPassword(null);
        usuario.setNombre(nombre != null && !nombre.isBlank() ? nombre : email);
        usuario.setAuthProvider(authProvider);
        usuario.setProviderUserId(providerUserId);
        usuario.setRol(resolveRoleByEmail(email));
        return usuarioRepository.save(usuario);
    }

    private RolUsuario resolveRoleByEmail(String email) {
        return email != null && email.toLowerCase().endsWith("@katedra.com")
                ? RolUsuario.ROLE_ADMIN
                : RolUsuario.ROLE_PROFESOR;
    }

    private UsuarioDTO mapToDTO(Usuario usuario) {
        return new UsuarioDTO(usuario.getId(), usuario.getEmail(), usuario.getNombre(), usuario.getRol());
    }
}
