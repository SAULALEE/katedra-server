package Katedra.Server.repository;

import Katedra.Server.model.Usuario;
import Katedra.Server.model.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, String> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByAuthProviderAndProviderUserId(AuthProvider authProvider, String providerUserId);

    @Modifying
    @Transactional
    @Query("update Usuario u set u.aiGenerationCount = u.aiGenerationCount + :cantidad where u.id = :usuarioId")
    int incrementAiGenerationCount(@Param("usuarioId") String usuarioId, @Param("cantidad") long cantidad);
}
