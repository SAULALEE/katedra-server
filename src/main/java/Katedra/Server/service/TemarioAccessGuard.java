package Katedra.Server.service;

import Katedra.Server.model.Temario;
import Katedra.Server.repository.TemarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single place to resolve a temario that the caller is allowed to touch.
 *
 * <p>Mirrors the ownership check {@code ContenidoTemarioService} keeps privately; new services use
 * this one instead of copying it. Folding the existing service onto this component is a separate
 * refactor, since it would rewrite most of that service's test setup.
 */
@Component
public class TemarioAccessGuard {

    private final TemarioRepository temarioRepository;

    public TemarioAccessGuard(TemarioRepository temarioRepository) {
        this.temarioRepository = temarioRepository;
    }

    /**
     * @throws ResponseStatusException 404 if it does not exist (or was soft-deleted), 403 if it
     *                                 belongs to another user
     */
    public Temario findOwnedTemario(String temarioId, String userEmail) {
        Temario temario = temarioRepository.findById(temarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Temario no encontrado"));

        if (!temario.getUsuario().getEmail().equals(userEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado a este temario");
        }
        return temario;
    }
}
