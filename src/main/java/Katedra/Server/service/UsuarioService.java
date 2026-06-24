package Katedra.Server.service;

import Katedra.Server.dto.UsuarioDTO;
import Katedra.Server.dto.UsuarioUpdateRequestDTO;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public List<UsuarioDTO> getAllUsuarios() {
        return usuarioRepository.findAll().stream()
                .map(this::mapToDTO)
                .toList();
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
        if (request.rol() != RolUsuario.ROLE_ADMIN && request.rol() != RolUsuario.ROLE_USER) {
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
