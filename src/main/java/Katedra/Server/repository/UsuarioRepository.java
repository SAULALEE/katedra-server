package Katedra.Server.repository;

import Katedra.Server.model.Usuario;
import Katedra.Server.model.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, String> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByAuthProviderAndProviderUserId(AuthProvider authProvider, String providerUserId);
}
