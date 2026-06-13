package Katedra.Server.service;

import Katedra.Server.dto.TemarioRequestDTO;
import Katedra.Server.dto.TemarioResponseDTO;
import Katedra.Server.model.Temario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.TemarioRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TemarioService {

    private final TemarioRepository temarioRepository;
    private final UsuarioRepository usuarioRepository;

    public TemarioService(TemarioRepository temarioRepository, UsuarioRepository usuarioRepository) {
        this.temarioRepository = temarioRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public TemarioResponseDTO createTemario(String userEmail, TemarioRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Temario temario = new Temario(
                usuario,
                request.titulo(),
                request.descripcion(),
                request.gradoAcademico(),
                request.asignatura()
        );

        Temario saved = temarioRepository.save(temario);
        return mapToDTO(saved);
    }

    public List<TemarioResponseDTO> getTemariosByUser(String userEmail) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return temarioRepository.findByUsuarioIdOrderByCreatedAtAsc(usuario.getId())
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public TemarioResponseDTO getTemarioById(String id, String userEmail) {
        Temario temario = temarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Temario no encontrado"));

        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new RuntimeException("Acceso denegado a este temario");
        }

        return mapToDTO(temario);
    }

    public void deleteTemario(String id, String userEmail) {
        Temario temario = temarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Temario no encontrado"));

        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new RuntimeException("Acceso denegado a este temario");
        }

        temarioRepository.delete(temario);
    }

    private TemarioResponseDTO mapToDTO(Temario temario) {
        return new TemarioResponseDTO(
                temario.getId(),
                temario.getTitulo(),
                temario.getDescripcion(),
                temario.getGradoAcademico(),
                temario.getAsignatura(),
                temario.getCreatedAt(),
                temario.getUpdatedAt()
        );
    }
}
