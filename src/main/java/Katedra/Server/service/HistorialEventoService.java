package Katedra.Server.service;

import Katedra.Server.dto.HistorialEventoResponseDTO;
import Katedra.Server.model.HistorialEvento;
import Katedra.Server.model.Temario;
import Katedra.Server.model.TipoEventoHistorial;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.HistorialEventoRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Append-only content history: every temario lifecycle event (create, edit, favorite toggle,
 * material generation, delete) is written here and never removed, including by its own owner —
 * this is a read-only audit trail, not a browsable log the user curates.
 */
@Service
public class HistorialEventoService {

    /** Caps how far back a user's history reaches so the timeline query stays cheap indefinitely. */
    private static final int LIMITE_EVENTOS = 200;

    private final HistorialEventoRepository historialEventoRepository;
    private final UsuarioRepository usuarioRepository;

    public HistorialEventoService(
            HistorialEventoRepository historialEventoRepository,
            UsuarioRepository usuarioRepository) {
        this.historialEventoRepository = historialEventoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public void registrar(Temario temario, TipoEventoHistorial tipo, String detalle) {
        Usuario usuario = temario.getUsuario();
        HistorialEvento evento = new HistorialEvento(
                usuario.getId(),
                temario.getId(),
                temario.getTitulo(),
                temario.getAsignatura() != null ? temario.getAsignatura().getNombre() : null,
                tipo,
                detalle);
        historialEventoRepository.save(evento);
    }

    @Transactional(readOnly = true)
    public List<HistorialEventoResponseDTO> listarHistorial(String userEmail) {
        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return historialEventoRepository
                .findByUsuarioIdOrderByCreatedAtDesc(usuario.getId(), PageRequest.of(0, LIMITE_EVENTOS))
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    private HistorialEventoResponseDTO mapToDTO(HistorialEvento evento) {
        return new HistorialEventoResponseDTO(
                evento.getId(),
                evento.getTemarioId(),
                evento.getTemarioTitulo(),
                evento.getAsignaturaNombre(),
                evento.getTipo(),
                evento.getDetalle(),
                evento.getCreatedAt());
    }
}
