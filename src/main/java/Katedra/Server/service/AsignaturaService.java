package Katedra.Server.service;

import Katedra.Server.dto.AsignaturaRequestDTO;
import Katedra.Server.dto.AsignaturaResponseDTO;
import Katedra.Server.model.Asignatura;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.AsignaturaRepository;
import Katedra.Server.repository.TemarioRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AsignaturaService {

    private final AsignaturaRepository asignaturaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TemarioRepository temarioRepository;

    public AsignaturaService(
            AsignaturaRepository asignaturaRepository,
            UsuarioRepository usuarioRepository,
            TemarioRepository temarioRepository) {
        this.asignaturaRepository = asignaturaRepository;
        this.usuarioRepository = usuarioRepository;
        this.temarioRepository = temarioRepository;
    }

    @Transactional
    public AsignaturaResponseDTO create(String userEmail, AsignaturaRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        Asignatura asignatura = new Asignatura(
                usuario,
                request.nombre().trim(),
                normalizeDescription(request.descripcion()));
        return map(asignaturaRepository.save(asignatura));
    }

    public List<AsignaturaResponseDTO> findAll(String userEmail) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        return asignaturaRepository.findByUsuarioIdOrderByNombreAsc(usuario.getId())
                .stream()
                .map(this::map)
                .toList();
    }

    public AsignaturaResponseDTO findById(String id, String userEmail) {
        return map(findOwned(id, userEmail));
    }

    @Transactional
    public void delete(String id, String userEmail) {
        Asignatura asignatura = findOwned(id, userEmail);
        if (temarioRepository.existsByAsignaturaId(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La asignatura contiene temarios; primero deben eliminarse sus temarios");
        }
        asignaturaRepository.delete(asignatura);
    }

    private Asignatura findOwned(String id, String userEmail) {
        return asignaturaRepository.findByIdAndUsuarioEmail(id, userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asignatura no encontrada"));
    }

    private AsignaturaResponseDTO map(Asignatura asignatura) {
        return new AsignaturaResponseDTO(
                asignatura.getId(),
                asignatura.getNombre(),
                asignatura.getDescripcion());
    }

    private String normalizeDescription(String descripcion) {
        return descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
    }
}
