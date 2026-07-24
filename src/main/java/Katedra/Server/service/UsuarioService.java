package Katedra.Server.service;

import Katedra.Server.dto.UsuarioCreateRequestDTO;
import Katedra.Server.dto.UsuarioCreateResponseDTO;
import Katedra.Server.dto.UsuarioDTO;
import Katedra.Server.dto.UsuarioUpdateRequestDTO;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UsuarioService {

    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
    private static final int TEMPORARY_PASSWORD_LENGTH = 14;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UsuarioDTO> getAllUsuarios() {
        return usuarioRepository.findAll().stream()
                .map(this::mapToDTO)
                .toList();
    }

    /**
     * Creates a new administrator account with a random temporary password.
     * This endpoint only creates ROLE_ADMIN accounts; teachers self-register via /auth/register.
     */
    public UsuarioCreateResponseDTO createAdmin(UsuarioCreateRequestDTO request) {
        if (request.nombre() == null || request.nombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es obligatorio");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El email es obligatorio");
        }
        usuarioRepository.findByEmail(request.email().trim()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
        });

        String temporaryPassword = generateTemporaryPassword();
        Usuario usuario = new Usuario(
                request.email().trim(),
                passwordEncoder.encode(temporaryPassword),
                request.nombre().trim(),
                RolUsuario.ROLE_ADMIN
        );

        Usuario saved = usuarioRepository.save(usuario);
        return new UsuarioCreateResponseDTO(mapToDTO(saved), temporaryPassword);
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    public UsuarioDTO getUsuarioById(String id) {
        return mapToDTO(findUsuarioById(id));
    }

    public UsuarioDTO updateUsuario(String id, UsuarioUpdateRequestDTO request) {
        Usuario usuario = findUsuarioById(id);

        validateRequest(request);
        validateEmailUniqueness(request.email(), usuario.getId());

        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(request.email().trim());
        usuario.setRol(request.rol());
        usuario.setUpdatedAt(LocalDateTime.now());

        return mapToDTO(usuarioRepository.save(usuario));
    }

    public void deleteUsuario(String id, String authenticatedEmail) {
        Usuario usuario = findUsuarioById(id);

        if (usuario.getEmail().equalsIgnoreCase(authenticatedEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes eliminar tu propia cuenta");
        }

        usuarioRepository.delete(usuario);
    }

    private Usuario findUsuarioById(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private void validateRequest(UsuarioUpdateRequestDTO request) {
        if (request.nombre() == null || request.nombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es obligatorio");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El email es obligatorio");
        }
        if (request.rol() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rol es obligatorio");
        }
        if (request.rol() != RolUsuario.ROLE_ADMIN
                && request.rol() != RolUsuario.ROLE_PROFESOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol no permitido");
        }
    }

    private void validateEmailUniqueness(String email, String currentUserId) {
        usuarioRepository.findByEmail(email.trim())
                .filter(existingUser -> !existingUser.getId().equals(currentUserId))
                .ifPresent(existingUser -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
                });
    }

    private UsuarioDTO mapToDTO(Usuario usuario) {
        return new UsuarioDTO(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.getCreatedAt()
        );
    }
}
