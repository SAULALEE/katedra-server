package Katedra.Server.repository;

import Katedra.Server.model.PasswordRecoveryToken;
import Katedra.Server.model.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordRecoveryTokenRepository extends JpaRepository<PasswordRecoveryToken, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordRecoveryToken token where token.tokenHash = :tokenHash")
    Optional<PasswordRecoveryToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    long countByUsuarioAndCreatedAtAfter(Usuario usuario, LocalDateTime since);
}
