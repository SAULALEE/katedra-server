package Katedra.Server.teacher.repository;

import Katedra.Server.teacher.model.ContenidoTemario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContenidoTemarioRepository extends JpaRepository<ContenidoTemario, String> {
    Optional<ContenidoTemario> findByTemarioId(String temarioId);
}
